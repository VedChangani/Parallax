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

/**
 * The only write path to {@link Strategy}/{@link StrategyVersion} (D-31).
 * Every operation is owner-scoped: the owner always comes from the caller's
 * {@link UserId}, never from a request body, and every repository access
 * used here takes that owner id (D-31 §10).
 *
 * <p><strong>Transactions.</strong> {@link #createStrategy} and {@link
 * #createVersion} use an explicit {@link TransactionTemplate} rather than
 * {@code @Transactional}, so the D-30 {@link StrategyDefinitionCodec#encode}
 * call — which does real work (canonicalization, hashing) but touches no
 * database state — visibly happens <em>before</em> the transaction opens
 * (D-31 §4). Every other method uses {@code @Transactional} directly. The
 * isolation level is pinned to {@code READ COMMITTED} — PostgreSQL's own
 * default — because {@link #createVersion}'s locking algorithm depends on a
 * {@code SELECT ... FOR UPDATE} re-reading the latest committed row after
 * waiting on the lock; raising it to {@code REPEATABLE READ} or {@code
 * SERIALIZABLE} would change that.
 *
 * <p><strong>Every write to a {@link Strategy} row goes through {@link
 * StrategyRepository#lockByIdAndOwnerId}</strong> — including a metadata
 * PATCH — never only {@link StrategyRepository#findByIdAndOwnerId}, per
 * D-31 §4.
 */
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

    /**
     * Creates a brand-new {@link Strategy} at version 1, atomically. No lock
     * is required: the new row is invisible to any other transaction until
     * commit, so there is nothing to race except the {@code (owner, name)}
     * uniqueness constraint, which the database itself decides (D-31 §4).
     */
    public StrategySummary createStrategy(UserId owner, String name, String description,
                                           StrategyDefinition definition) {
        Objects.requireNonNull(owner, "owner must not be null");
        Objects.requireNonNull(name, "name must not be null");
        Objects.requireNonNull(description, "description must not be null");
        Objects.requireNonNull(definition, "definition must not be null");

        // D-30 canonicalization/hashing happens before any transaction opens.
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

    /**
     * Metadata only — {@code name}/{@code description} — never touches any
     * {@link StrategyVersion}. Both fields are required (D-31: PATCH is a
     * full metadata replacement, not a partial update).
     */
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

    /**
     * Allocates {@code latest + 1} under the owner-scoped row lock and
     * inserts the new immutable version (D-31 §4). If anything in the
     * callback throws, the whole transaction — including the parent's
     * {@code latest_version_number} increment — rolls back, so a failed
     * creation never consumes a version number.
     */
    public StrategyVersionDetail createVersion(UserId owner, long strategyId, StrategyDefinition definition) {
        Objects.requireNonNull(owner, "owner must not be null");
        Objects.requireNonNull(definition, "definition must not be null");

        // D-30 canonicalization/hashing happens before any transaction opens.
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
        // Strategy existence/ownership is checked first, so a nonexistent or
        // cross-owner strategy id 404s rather than silently returning an
        // empty list.
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
        // D-30: never trust the stored jsonb text directly — decode re-parses,
        // re-maps, re-encodes canonically, and re-hashes before trusting it.
        StrategyDefinition definition = codec.decode(version.definitionSchemaVersion(), version.definitionJson(),
                version.definitionHash());
        return new StrategyVersionDetail(StrategyVersionSummary.of(version), definition);
    }

    // --- internal helpers ----------------------------------------------------

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

    /**
     * Matches a failed write against a specific database constraint name,
     * never against SQL state or message text alone where a constraint name
     * is available. Any other constraint violation propagates unchanged and
     * becomes a generic 500 (D-31 §11) — this method only ever narrows the
     * two conflicts D-31 defines, never widens what counts as a duplicate.
     */
    private static boolean isConstraint(DataIntegrityViolationException e, String constraintName) {
        Throwable cause = e.getCause();
        if (cause instanceof ConstraintViolationException cve && cve.getConstraintName() != null) {
            return cve.getConstraintName().equalsIgnoreCase(constraintName);
        }
        String message = e.getMostSpecificCause().getMessage();
        return message != null && message.contains(constraintName);
    }
}
