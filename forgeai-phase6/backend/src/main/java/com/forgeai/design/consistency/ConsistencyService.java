package com.forgeai.design.consistency;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.forgeai.design.DesignDocumentStore;
import com.forgeai.design.DesignDtos.Area;
import com.forgeai.design.DesignDtos.Finding;
import com.forgeai.design.DesignKind;
import com.forgeai.design.api.ApiDesignContent;
import com.forgeai.design.api.ApiRules;
import com.forgeai.design.database.DatabaseDesignContent;
import com.forgeai.design.database.DatabaseRules;
import com.forgeai.requirement.RequirementDtos.RequirementResponse;
import com.forgeai.requirement.RequirementService;
import com.forgeai.requirement.RequirementType;

import tools.jackson.databind.json.JsonMapper;

/**
 * Consistency checks across requirements, data model and API, recomputed on every read so they
 * always reflect the current documents:
 *
 * <ul>
 *   <li>the data model on its own (primary keys, foreign keys);</li>
 *   <li>the API against the data model (entities, columns, unused parts);</li>
 *   <li>functional requirements against both, once the API exists.</li>
 * </ul>
 */
@Service
public class ConsistencyService {

    private final DesignDocumentStore store;
    private final RequirementService requirements;
    private final JsonMapper jsonMapper;

    ConsistencyService(DesignDocumentStore store, RequirementService requirements, JsonMapper jsonMapper) {
        this.store = store;
        this.requirements = requirements;
        this.jsonMapper = jsonMapper;
    }

    @Transactional(readOnly = true)
    public List<Finding> findings(UUID projectId) {
        DatabaseDesignContent database = store.find(projectId, DesignKind.DATABASE)
                .map(d -> jsonMapper.readValue(d.getContentJson(), DatabaseDesignContent.class))
                .orElse(null);
        ApiDesignContent api = store.find(projectId, DesignKind.API)
                .map(d -> jsonMapper.readValue(d.getContentJson(), ApiDesignContent.class))
                .orElse(null);

        List<Finding> findings = new ArrayList<>();
        if (database != null) findings.addAll(DatabaseRules.findings(database));
        if (api != null) {
            findings.addAll(ApiRules.findings(api, database));

            Set<String> covered = new HashSet<>(ApiRules.coveredRequirements(api));
            if (database != null) covered.addAll(DatabaseRules.coveredRequirements(database));
            for (RequirementResponse requirement : requirements.snapshot(projectId).approved()) {
                if (requirement.type() == RequirementType.FUNCTIONAL && !covered.contains(requirement.code())) {
                    findings.add(Finding.warning(Area.REQUIREMENTS, requirement.code() + " (" + requirement.title()
                            + ") is not served by any entity or endpoint."));
                }
            }
        }
        return findings;
    }
}
