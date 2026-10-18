package com.forgeai.design.database;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import com.forgeai.architecture.ArchitectureDtos.RequirementRef;
import com.forgeai.common.review.ReviewStatus;
import com.forgeai.design.DesignDtos.DesignState;
import com.forgeai.design.DesignDtos.Finding;

/** Request and response bodies of the database design API. */
public final class DatabaseDtos {

    private DatabaseDtos() {
    }

    public record UpdateDatabaseDesign(@NotNull @Valid DatabaseDesignContent content, @NotNull Integer version) {
    }

    /**
     * Everything the database design page needs.
     *
     * @param design       null until the first design is generated
     * @param requirements the approved requirements, which entities refer to
     * @param blocker      why generating is not possible right now; null when it is
     * @param findings     consistency checks across requirements, data model and API
     */
    public record DatabaseDesignOverview(
            DesignState<DatabaseDesignContent> design,
            List<RequirementRef> requirements,
            boolean canGenerate,
            String blocker,
            List<Finding> findings) {
    }

    /** The data model as the API design sees it. */
    public record DatabaseSnapshot(ReviewStatus status, DatabaseDesignContent content, String fingerprint) {
    }
}
