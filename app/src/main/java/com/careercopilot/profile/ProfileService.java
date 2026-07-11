package com.careercopilot.profile;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional
public class ProfileService {

    private final MasterProfileRepository masterProfileRepository;
    private final ProfileFactRepository profileFactRepository;

    public ProfileService(MasterProfileRepository masterProfileRepository, ProfileFactRepository profileFactRepository) {
        this.masterProfileRepository = masterProfileRepository;
        this.profileFactRepository = profileFactRepository;
    }

    public Optional<MasterProfile> getProfileByUserId(String userId) {
        return masterProfileRepository.findByUserId(userId)
                .map(MasterProfileEntity::toDomain);
    }

    public MasterProfile saveProfile(String userId, MasterProfile profile) {
        // Enforce the correct userId on the profile
        MasterProfile profileWithUser = new MasterProfile(
                profile.id(),
                userId,
                profile.workAuthorization(),
                profile.visaSponsorshipNeeded(),
                profile.salaryFloor(),
                profile.locations(),
                profile.remotePreference(),
                profile.blocklistCompanies(),
                profile.dailyApplicationCap(),
                profile.autonomyThreshold()
        );

        MasterProfileEntity entity = new MasterProfileEntity(profileWithUser);
        MasterProfileEntity saved = masterProfileRepository.save(entity);
        return saved.toDomain();
    }

    public List<ProfileFact> getFactsByMasterProfileId(UUID masterProfileId) {
        return profileFactRepository.findByMasterProfileId(masterProfileId)
                .stream()
                .map(ProfileFactEntity::toDomain)
                .collect(Collectors.toList());
    }

    public ProfileFact addFact(ProfileFact fact) {
        ProfileFactEntity entity = new ProfileFactEntity(fact);
        ProfileFactEntity saved = profileFactRepository.save(entity);
        return saved.toDomain();
    }

    public ProfileFact updateFact(UUID factId, ProfileFact factUpdate) {
        ProfileFactEntity existing = profileFactRepository.findById(factId)
                .orElseThrow(() -> new IllegalArgumentException("ProfileFact not found with id: " + factId));

        existing.setType(factUpdate.type());
        existing.setEmployer(factUpdate.employer());
        existing.setTitle(factUpdate.title());
        existing.setStartDate(factUpdate.startDate());
        existing.setEndDate(factUpdate.endDate());
        existing.setBulletText(factUpdate.bulletText());
        existing.setSkills(factUpdate.skills());

        ProfileFactEntity saved = profileFactRepository.save(existing);
        return saved.toDomain();
    }

    public void deleteFact(UUID factId) {
        if (!profileFactRepository.existsById(factId)) {
            throw new IllegalArgumentException("ProfileFact not found with id: " + factId);
        }
        profileFactRepository.deleteById(factId);
    }
}
