package com.forgeai.docs;

import java.util.List;

import com.forgeai.architecture.ArchitectureSnapshot;
import com.forgeai.code.CodeService;
import com.forgeai.code.CodeService.SourceFile;
import com.forgeai.common.util.Fingerprints;
import com.forgeai.design.api.ApiDtos.ApiSnapshot;
import com.forgeai.design.database.DatabaseDtos.DatabaseSnapshot;
import com.forgeai.project.ProjectResponse;
import com.forgeai.requirement.RequirementSnapshot;
import com.forgeai.task.TaskService.CodeTask;

/**
 * Everything documentation is made from, read at one moment.
 *
 * @param blocker why documentation cannot be made yet (part of the design not approved); null when it can.
 *                The snapshots are only complete when this is null.
 */
record DocsInputs(
        ProjectResponse project,
        String blocker,
        RequirementSnapshot requirements,
        ArchitectureSnapshot architecture,
        DatabaseSnapshot database,
        ApiSnapshot api,
        List<CodeTask> tasks,
        List<SourceFile> files) {

    /** Changes when the approved design or the accepted code changes, so a document can say it is outdated. */
    String fingerprint() {
        return Fingerprints.sha256(
                requirements.approvedFingerprint(),
                architecture == null ? "" : architecture.fingerprint(),
                database == null ? "" : database.fingerprint(),
                api == null ? "" : api.fingerprint(),
                CodeService.fingerprint(files));
    }
}
