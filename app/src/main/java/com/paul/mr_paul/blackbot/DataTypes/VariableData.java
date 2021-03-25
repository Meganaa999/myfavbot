package com.paul.mr_paul.blackbot.DataTypes;

public class VariableData {

    private String var;
    private String value;
    private String timeStamp;

    public VariableData(String var, String value, String timeStamp){
        this.var = var;
        this.value = value;
        this.timeStamp = timeStamp;
    }

    public String getVariable(){
        return var;
    }

    public String getValue(){
        return value;
    }

    public String getTimeStamp(){
        return timeStamp;
    }

}