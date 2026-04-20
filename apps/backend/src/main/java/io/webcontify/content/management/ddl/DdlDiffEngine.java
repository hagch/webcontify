package io.webcontify.content.management.ddl;

import io.webcontify.content.definition.repository.entity.ContentFieldEntity;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Computes the list of DDL changes needed for a content definition
 * based on the pending_action flags on its field entities.
 */
@Component
public class DdlDiffEngine {

    /**
     * Computes DDL changes for a definition.
     *
     * @param isNewTable     true if no field for this definition has been published yet (first publish)
     * @param pendingFields  fields with pending_action != null
     * @return ordered list of DDL changes
     */
    public List<DdlChange> diff(boolean isNewTable, List<ContentFieldEntity> pendingFields) {
        var changes = new ArrayList<DdlChange>();

        if (isNewTable) {
            changes.add(new DdlChange(DdlAction.CREATE_TABLE, null));
            return changes;
        }

        for (ContentFieldEntity field : pendingFields) {
            String action = field.getPendingAction();
            if (action == null) continue;

            switch (action) {
                case "ADDED" -> {
                    changes.add(new DdlChange(DdlAction.ADD_COLUMN, field));
                    if (field.isSortable()) {
                        changes.add(new DdlChange(DdlAction.ADD_INDEX, field));
                    }
                    if (field.isUniqueValue()) {
                        changes.add(new DdlChange(DdlAction.ADD_UNIQUE_INDEX, field));
                    }
                }
                case "REMOVED" -> changes.add(new DdlChange(DdlAction.DROP_COLUMN, field));
                case "MODIFIED" -> {
                    changes.add(new DdlChange(DdlAction.DROP_COLUMN, field));
                    changes.add(new DdlChange(DdlAction.ADD_COLUMN, field));
                    if (field.isSortable()) {
                        changes.add(new DdlChange(DdlAction.ADD_INDEX, field));
                    }
                    if (field.isUniqueValue()) {
                        changes.add(new DdlChange(DdlAction.ADD_UNIQUE_INDEX, field));
                    }
                }
            }
        }

        return changes;
    }
}
