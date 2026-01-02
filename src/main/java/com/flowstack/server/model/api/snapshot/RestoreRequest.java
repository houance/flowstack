package com.flowstack.server.model.api.snapshot;

import com.flowstack.server.model.db.SnapshotMetaEntity;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RestoreRequest {
    private SnapshotMetaEntity snapshotMetaEntity;

    private List<SnapshotItemDTO> snapshotItemDTOList;
}
