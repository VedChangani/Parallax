package in.vedchangani.parallax.backend.user;

import org.springframework.data.repository.Repository;

import java.util.Optional;

public interface AppUserRepository extends Repository<AppUser, Long> {

    Optional<AppUser> findByUsername(String username);

    Optional<AppUser> findById(long id);

    <S extends AppUser> S saveAndFlush(S appUser);
}
