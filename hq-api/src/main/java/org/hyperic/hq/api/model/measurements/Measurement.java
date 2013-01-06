package org.hyperic.hq.api.model.measurements;


import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlType;

import org.hyperic.hq.api.model.RestApiConstants;


@XmlAccessorType(XmlAccessType.FIELD)
@XmlRootElement(name = "measurement", namespace=RestApiConstants.SCHEMA_NAMESPACE)
@XmlType(name="MeasurementType", namespace=RestApiConstants.SCHEMA_NAMESPACE)
public class Measurement extends MetricGroupBase {
    @XmlAttribute
    protected Integer id;
    @XmlAttribute
    protected String alias;
    @XmlAttribute
    protected String name;
	@XmlAttribute
	protected Long interval;
    @XmlAttribute
    protected Double avg;
    @XmlAttribute
    protected Boolean enabled;
    @XmlAttribute
    protected Boolean indicator;
    @XmlAttribute
    protected Double average = null;	
    
	
	public Measurement() {}
	public Integer getId() {
        return id;
    }
    public void setId(Integer id) {
        this.id = id;
    }
    public String getAlias() {
        return alias;
    }
    public void setAlias(String alias) {
        this.alias = alias;
    }
    public Long getInterval() {
		return interval;
	}
	public void setInterval(Long interval) {
		this.interval = interval;
	}
	public String getName() {
		return name;
	}
	public void setName(String name) {
		this.name = name;
	}
    public double getAverage() {
        return average.doubleValue();
    }
    public void setAverage(Double avg) {
        this.average = avg;
    }
    public Boolean getIndicator() {
        return indicator;
    }
    public void setIndicator(Boolean indicator) {
        this.indicator = indicator;
    }
    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }
    public Boolean getEnabled() {
        return this.enabled;
    }
}
