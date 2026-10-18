package com.forgeai.design.api;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import com.forgeai.architecture.ArchitectureDtos.RequirementRef;
import com.forgeai.common.review.ReviewStatus;
import com.forgeai.design.DesignDtos.DesignState;
import com.forgeai.design.DesignDtos.Finding;
import com.forgeai.design.database.DatabaseDesignContent;

/** Request and response bodies of the API design endpoints. */
public final class ApiDtos {

    private ApiDtos() {
    }

    /** The API as the development backlog sees it. */
    public record ApiSnapshot(ReviewStatus status, ApiDesignContent content, String fingerprint) {
    }

    public record UpdateApiDesign(@NotNull @Valid ApiDesignContent content, @NotNull Integer version) {
    }

    /**
     * Everything the API design page needs.
     *
     * @param design       null until the first design is generated
     * @param requirements the approved requirements, which endpoints refer to
     * @param database     the current data model (entities and columns), null if there is none
     * @param blocker      why generating is not possible right now; null when it is
     * @param findings     consistency checks across requirements, data model and API
     */
    public record ApiDesignOverview(
            DesignState<ApiDesignContent> design,
            List<RequirementRef> requirements,
            DatabaseDesignContent database,
            boolean canGenerate,
            String blocker,
            List<Finding> findings) {
    }
}
