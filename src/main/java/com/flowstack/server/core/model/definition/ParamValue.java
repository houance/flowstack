package com.flowstack.server.core.model.definition;

import com.flowstack.server.core.enums.ParamSourceType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ParamValue {

    private Object value;

    private ParamSourceType paramSourceType; // param 来源

    public ParamValue(Object value) {
        this.value = value;
        this.paramSourceType = ParamSourceType.MANUAL;
    }
}
