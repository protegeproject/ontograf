package org.protege.ontograf.common.util;

public enum NodeOWLClassTooltipType {
	TITLE("Title"), URI("URI"), SUPERCLASSES("Superclasses"), EQUIVALENT_CLASSES("Equivalent classes"), 
	DISJOINT_CLASSES("Disjoint classes"), ANNOTATIONS("Annotations");
	
	private final String value;
	private boolean enabled;
	
	NodeOWLClassTooltipType(String value) {
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
