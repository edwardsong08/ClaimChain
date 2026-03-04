package com.claimchain.backend.scoring;

import java.util.LinkedHashMap;
import java.util.Map;

public class ScoringContribution {

    private String ruleId;
    private String group;
    private Integer delta;
    private String reason;
    private Map<String, Object> fieldsUsed = new LinkedHashMap<>();

    public String getRuleId() {
        return ruleId;
    }

    public void setRuleId(String ruleId) {
        this.ruleId = ruleId;
    }

    public String getGroup() {
        return group;
    }

    public void setGroup(String group) {
        this.group = group;
    }

    public Integer getDelta() {
        return delta;
    }

    public void setDelta(Integer delta) {
        this.delta = delta;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public Map<String, Object> getFieldsUsed() {
        return fieldsUsed;
    }

    public void setFieldsUsed(Map<String, Object> fieldsUsed) {
        this.fieldsUsed = fieldsUsed;
    }
}
