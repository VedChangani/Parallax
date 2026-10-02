package in.vedchangani.parallax.backend.strategy;

import in.vedchangani.parallax.backend.strategy.definition.CanonicalStrategyDefinition;
import in.vedchangani.parallax.backend.strategy.definition.StrategyDefinitionCodec;
import in.vedchangani.parallax.backend.user.UserId;
import in.vedchangani.parallax.engine.strategy.StrategyDefinition;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Objects;

@Service
public class StrategyService {

    private final StrategyRepository strategyRepository;
    private final StrategyVersionRepository versionRepository;
    private final StrategyDefinitionCodec codec;
    private final TransactionTemplate transactionTemplate;

    public StrategyService(StrategyRepository strategyRepository, StrategyVersionRepository versionRepository,
                            StrategyDefinitionCodec codec, PlatformTransactionManager transactionManager) {
        this.strategyRepository = strategyRepository;
        this.versionRepository = versionRepository;
        this.codec = codec;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.transactionTemplate.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
    }

    public StrategySummary createStrategy(UserId owner, String name, String description,
                                           StrategyDefinition definition) {
        Objects.requireNonNull(owner, "owner must not be null");
        Objects.requireNonNull(name, "name must not be null");
        Objects.requireNonNull(description, "description must not be null");
        Objects.requireNonNull(definition, "definition must not be null");

        CanonicalStrategyDefinition canonical = codec.encode(definition);

        Strategy strategy = transactionTemplate.execute(status -> {
            Strategy created = new Strategy(owner.value(), name, description);
            Strategy saved = saveStrategyOrThrowDuplicate(created, name);
            StrategyVersion version = new StrategyVersion(saved.id(), 1, canonical);
            saveVersionOrThrowConflict(version, saved.id());
            return saved;
        });

        return StrategySummary.of(strategy);
    }

    @Transactional(readOnly = true)
    public List<StrategySummary> listStrategies(UserId owner) {
        Objects.requireNonNull(owner, "owner must not be null");
        return strategyRepository.findByOwnerIdOrderByIdAsc(owner.value()).stream()
                .map(StrategySummary::of)
                .toList();
    }

    @Transactional(readOnly = true)
    public StrategySummary getStrategy(UserId owner, long strategyId) {
        Objects.requireNonNull(owner, "owner must not be null");
        return StrategySummary.of(loadOwned(owner, strategyId));
    }

    @Transactional
    public StrategySummary updateStrategyMetadata(UserId owner, long strategyId, String name, String description) {
        Objects.requireNonNull(owner, "owner must not be null");
        Objects.requireNonNull(name, "name must not be null");
        Objects.requireNonNull(description, "description must not be null");

        Strategy strategy = strategyRepository.lockByIdAndOwnerId(strategyId, owner.value())
                .orElseThrow(() -> new StrategyNotFoundException(strategyId));
        strategy.updateMetadata(name, description);
        Strategy saved = saveStrategyOrThrowDuplicate(strategy, name);
        return StrategySummary.of(saved);
    }

    public StrategyVersionDetail createVersion(UserId owner, long strategyId, StrategyDefinition definition) {
        Objects.requireNonNull(owner, "owner must not be null");
        Objects.requireNonNull(definition, "definition must not be null");

        CanonicalStrategyDefinition canonical = codec.encode(definition);

        StrategyVersion version = transactionTemplate.execute(status -> {
            Strategy strategy = strategyRepository.lockByIdAndOwnerId(strategyId, owner.value())
                    .orElseThrow(() -> new StrategyNotFoundException(strategyId));
            int nextVersionNumber = strategy.allocateNextVersionNumber();
            strategyRepository.saveAndFlush(strategy);
            StrategyVersion created = new StrategyVersion(strategy.id(), nextVersionNumber, canonical);
            return saveVersionOrThrowConflict(created, strategyId);
        });

        return new StrategyVersionDetail(StrategyVersionSummary.of(version), definition);
    }

    @Transactional(readOnly = true)
    public List<StrategyVersionSummary> listVersions(UserId owner, long strategyId) {
        Objects.requireNonNull(owner, "owner must not be null");
        loadOwned(owner, strategyId);
        return versionRepository.findAllOwned(strategyId, owner.value()).stream()
                .map(StrategyVersionSummary::of)
                .toList();
    }

    @Transactional(readOnly = true)
    public StrategyVersionDetail getVersion(UserId owner, long strategyId, int versionNumber) {
        Objects.requireNonNull(owner, "owner must not be null");
        StrategyVersion version = versionRepository.findOwned(strategyId, owner.value(), versionNumber)
                .orElseThrow(() -> new StrategyVersionNotFoundException(strategyId, versionNumber));
        StrategyDefinition definition = codec.decode(version.definitionSchemaVersion(), version.definitionJson(),
                version.definitionHash());
        return new StrategyVersionDetail(StrategyVersionSummary.of(version), definition);
    }

    private Strategy loadOwned(UserId owner, long strategyId) {
        return strategyRepository.findByIdAndOwnerId(strategyId, owner.value())
                .orElseThrow(() -> new StrategyNotFoundException(strategyId));
    }

    private Strategy saveStrategyOrThrowDuplicate(Strategy strategy, String name) {
        try {
            return strategyRepository.saveAndFlush(strategy);
        } catch (DataIntegrityViolationException e) {
            if (isConstraint(e, "uq_strategy_owner_name")) {
                throw new DuplicateStrategyNameException(name);
            }
            throw e;
        }
    }

    private StrategyVersion saveVersionOrThrowConflict(StrategyVersion version, long strategyId) {
        try {
            return versionRepository.saveAndFlush(version);
        } catch (DataIntegrityViolationException e) {
            if (isConstraint(e, "uq_strategy_version_number")) {
                throw new StrategyVersionConflictException(strategyId);
            }
            throw e;
        }
    }

    private static boolean isConstraint(DataIntegrityViolationException e, String constraintName) {
        Throwable cause = e.getCause();
        if (cause instanceof ConstraintViolationException cve && cve.getConstraintName() != null) {
            return cve.getConstraintName().equalsIgnoreCase(constraintName);
        }
        String message = e.getMostSpecificCause().getMessage();
        return message != null && message.contains(constraintName);
    }
}
