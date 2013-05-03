package org.protege.ontograf.actions;

import java.awt.Component;

import javax.swing.Action;

import org.protege.ontograf.common.util.IconConstants;
import org.protege.ontograf.ui.TooltipConfigurationDialog;

import ca.uvic.cs.chisel.cajun.actions.CajunAction;
import edu.umd.cs.piccolo.PCanvas;

public class ConfigTooltipsAction extends CajunAction {
	private static final long serialVersionUID = 7241297162054742885L;

	private static final String ACTION_NAME = "Configure Node Tooltips";
	
	private TooltipConfigurationDialog tooltipDialog;
	
	public ConfigTooltipsAction(Component parent, PCanvas canvas) {
		super(ACTION_NAME, IconConstants.ICON_EDIT_NODE_TOOLTIP);
		
		putValue(Action.SHORT_DESCRIPTION, ACTION_NAME);
		
		tooltipDialog = new TooltipConfigurationDialog();
	}
	
	@Override
	public void doAction() {
		tooltipDialog.setVisible(true);
	}
}
