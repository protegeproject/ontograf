package org.protege.ontograf.common.util;

public enum NodeOWLIndividualTooltipType {
	TITLE("Title"), URI("URI"), SAME_INDIVIDUALS("Same individuals"), DIFFERENT_INDIVIDUALS("Different individuals"), 
	OBJECT_PROPERTY_ASSERTIONS("Object property assertions"), DATA_PROPERTY_ASSERTIONS("Data property assertions"), 
	NEGATIVE_OBJECT_PROPERTY_ASSERTIONS("Negative object property assertions"), NEGATIVE_DATA_PROPERTY_ASSERTIONS("Negative data property assertions"), ANNOTATIONS("Annotations");
	
	private final String value;
	private boolean enabled;
	
	NodeOWLIndividualTooltipType(String value) {
		this.value = value;
		this.enabled = true;
	}
	
	public boolean isEnabled() {
		return enabled;
	}
	
	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}
	
	public String toString() {
		return value;
	}
}
