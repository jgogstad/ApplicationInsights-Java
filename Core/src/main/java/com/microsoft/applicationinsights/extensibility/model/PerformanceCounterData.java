



//------------------------------------------------------------------------------
// <auto-generated>
//     This code was generated by a tool.
// 
//     Tool     : bondc, Version=3.0.1, Build=bond-git.retail.not
//     Template : Microsoft.Bond.Rules.dll#Java.tt
//     File     : com\microsoft\applicationinsights\extensibility\model\PerformanceCounterData.java
//
//     Changes to this file may cause incorrect behavior and will be lost when
//     the code is regenerated.
// </auto-generated>
//------------------------------------------------------------------------------
package com.microsoft.applicationinsights.extensibility.model;


/**
* *****************************************************************************                                Performance Counter type***************************************************************************** Instance of Performance Counter represents data for a specific Windows 	Performance Counter.
*/
@SuppressWarnings("all")
public class PerformanceCounterData
{
    //
    // Fields
    //

    // 10: Required int32 ver
    private int ver;

    // 20: Required string categoryName
    private String categoryName;

    // 40: Required string counterName
    private String counterName;

    // 50: Optional string instanceName
    private String instanceName;

    // 60: Optional string kind
    private String kind;

    // 70: Optional nullable<int32> count
    private Integer count;

    // 80: Optional nullable<double> min
    private Double min;

    // 90: Optional nullable<double> max
    private Double max;

    // 100: Optional nullable<double> stdDev
    private Double stdDev;

    // 110: Required double value
    private double value;

    // 120: Optional map<string, string> properties
    private java.util.HashMap<String, String> properties;

    /**
     * @return current value of ver property
     */
    public final int getVer() {
        return this.ver;
    }

    /**
     * @param value new value of ver property
     */
    public final void setVer(int value) {
        this.ver = value;
    }

    /**
     * @return current value of categoryName property
     */
    public final String getCategoryName() {
        return this.categoryName;
    }

    /**
     * @param value new value of categoryName property
     */
    public final void setCategoryName(String value) {
        this.categoryName = value;
    }

    /**
     * @return current value of counterName property
     */
    public final String getCounterName() {
        return this.counterName;
    }

    /**
     * @param value new value of counterName property
     */
    public final void setCounterName(String value) {
        this.counterName = value;
    }

    /**
     * @return current value of instanceName property
     */
    public final String getInstanceName() {
        return this.instanceName;
    }

    /**
     * @param value new value of instanceName property
     */
    public final void setInstanceName(String value) {
        this.instanceName = value;
    }

    /**
     * @return current value of kind property
     */
    public final String getKind() {
        return this.kind;
    }

    /**
     * @param value new value of kind property
     */
    public final void setKind(String value) {
        this.kind = value;
    }

    /**
     * @return current value of count property
     */
    public final Integer getCount() {
        return this.count;
    }

    /**
     * @param value new value of count property
     */
    public final void setCount(Integer value) {
        this.count = value;
    }

    /**
     * @return current value of min property
     */
    public final Double getMin() {
        return this.min;
    }

    /**
     * @param value new value of min property
     */
    public final void setMin(Double value) {
        this.min = value;
    }

    /**
     * @return current value of max property
     */
    public final Double getMax() {
        return this.max;
    }

    /**
     * @param value new value of max property
     */
    public final void setMax(Double value) {
        this.max = value;
    }

    /**
     * @return current value of stdDev property
     */
    public final Double getStdDev() {
        return this.stdDev;
    }

    /**
     * @param value new value of stdDev property
     */
    public final void setStdDev(Double value) {
        this.stdDev = value;
    }

    /**
     * @return current value of value property
     */
    public final double getValue() {
        return this.value;
    }

    /**
     * @param value new value of value property
     */
    public final void setValue(double value) {
        this.value = value;
    }

    /**
     * @return current value of properties property
     */
    public final java.util.HashMap<String, String> getProperties() {
        return this.properties;
    }

    /**
     * @param value new value of properties property
     */
    public final void setProperties(java.util.HashMap<String, String> value) {
        this.properties = value;
    }

    // Constructor
    public PerformanceCounterData() {
        reset();
    }

    /*
     * @see com.microsoft.bond.BondSerializable#reset()
     */
    public void reset() {
        reset("PerformanceCounterData", "com.microsoft.applicationinsights.extensibility.model.PerformanceCounterData");
    }

    protected void reset(String name, String qualifiedName) {
        
        ver = 1;
        categoryName = "";
        counterName = "";
        instanceName = "";
        kind = "";
        count = null;
        min = null;
        max = null;
        stdDev = null;
        value = 0;
        if (properties == null) {
            properties = new java.util.HashMap<String, String>();
        } else {
            properties.clear();
        }
    }
} // class PerformanceCounterData