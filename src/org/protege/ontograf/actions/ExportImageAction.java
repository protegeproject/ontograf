package org.protege.ontograf.actions;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

import javax.imageio.ImageIO;
import javax.swing.Action;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JViewport;
import javax.swing.filechooser.FileFilter;

import org.protege.ontograf.common.util.IconConstants;
import org.protege.ontograf.ui.OntoGrafFileFilter;

import ca.uvic.cs.chisel.cajun.actions.CajunAction;
import edu.umd.cs.piccolo.PCanvas;

public class ExportImageAction extends CajunAction {
	private static final long serialVersionUID = 7241297162054742885L;

	private static final String ACTION_NAME = "Export Graph as Image";
	
	private JFileChooser fileChooser;
	private Component parent;
	private PCanvas canvas;
	
	// booleans indicating whether the different formats are available
	private boolean jpegAvailable;
	private boolean pngAvailable;
	private boolean gifAvailable;
	
	// file filters for showing only a given type of file in the file dialog
	private OntoGrafFileFilter pngFileFilter;
	private OntoGrafFileFilter gifFileFilter;
	private OntoGrafFileFilter jpegFileFilter;
	
	public ExportImageAction(Component parent, PCanvas canvas) {
		super(ACTION_NAME, IconConstants.ICON_EXPORT_IMAGE);
		
		putValue(Action.SHORT_DESCRIPTION, ACTION_NAME);
		
		this.parent = parent;
		this.canvas = canvas;
		this.fileChooser = new JFileChooser();
		
		String[] formats = ImageIO.getWriterFormatNames();
		String allFormats = "";
		for (int i = 0; i < formats.length; i++) {
			String format = formats[i];
			if (!jpegAvailable && (format.toLowerCase().equals("jpeg") || format.toLowerCase().equals("jpg"))) {
				jpegAvailable = true;
				allFormats += "JPG ";
			} else if (!pngAvailable && format.toLowerCase().equals("png")) {
				pngAvailable = true;
				allFormats += "PNG ";
			} else if (!gifAvailable && format.toLowerCase().equals("gif")) {
				gifAvailable = true;
				allFormats += "GIF";
			}
		}
		
		fileChooser.setDialogTitle("Export to Image File (" + allFormats + ")");
		//File dir = (lastDirectory != null ? lastDirectory : new File(System.getProperty("user.dir")));
		//chooser.setCurrentDirectory(dir);
		// chooser.setAcceptAllFileFilterUsed(true);

		if (pngAvailable) {
			pngFileFilter = new OntoGrafFileFilter(new String[] { "png" }, "PNG Images");
			fileChooser.addChoosableFileFilter(pngFileFilter);
		}
		if (gifAvailable) {
			gifFileFilter = new OntoGrafFileFilter(new String[] { "gif" }, "GIF Images");
			fileChooser.addChoosableFileFilter(gifFileFilter);
		}
		if (jpegAvailable) {
			jpegFileFilter = new OntoGrafFileFilter(new String[] { "jpg", "jpeg" }, "JPEG Images");
			fileChooser.addChoosableFileFilter(jpegFileFilter);
		}
	}
	
	@Override
	public void doAction() {
		int result = fileChooser.showSaveDialog(parent);
		
		if(result == JFileChooser.CANCEL_OPTION) return;
		
		File file = fileChooser.getSelectedFile();
		FileFilter fileFilter = fileChooser.getFileFilter();
		String filePath = file.toString();
		String formatName = "";
		if (jpegFileFilter != null && jpegFileFilter.equals(fileFilter)) {
			formatName = "jpg";
			if (!filePath.toLowerCase().endsWith(".jpg") && !filePath.toLowerCase().endsWith(".jpeg")) {
				filePath += ".jpg";
				file = new File(filePath);
			}
		}
		if (pngFileFilter != null && pngFileFilter.equals(fileFilter)) {
			formatName = "png";
			if (!filePath.toLowerCase().endsWith(".png")) {
				filePath += ".png";
				file = new File(filePath);
			}
		}
		if (gifFileFilter != null && gifFileFilter.equals(fileFilter)) {
			formatName = "gif";
			if (!filePath.toLowerCase().endsWith(".gif")) {
				filePath += ".gif";
				file = new File(filePath);
			}
		}
		
		boolean saveImage = false;
		if (file.exists()) {
			String msg = "The file " + file.toString() + " already exists.\nDo you want to overwrite this existing file?";
			int opt = JOptionPane.showConfirmDialog(parent, msg,
					"Export Warning", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
			saveImage = (opt == JOptionPane.YES_OPTION);
		} else {
			try {
				file.createNewFile();
				saveImage = true;
			} catch (IOException e) {
				e.printStackTrace();
				String msg = "There was a problem creating " + file.toString() + ". The image has not been exported.";
				JOptionPane.showMessageDialog(parent, msg, "Export Error", JOptionPane.ERROR_MESSAGE);
			}
		}

		if (saveImage) {
			exportGraphAsImage(file, formatName);
		}
	}
	
	private void exportGraphAsImage(File file, String formatName) {
		boolean saved = false;
		// if the parent is a viewport then save the entire canvas, not just the visible part
		if (canvas.getParent() instanceof JViewport) {
			saved = saveViewport(file, formatName, canvas, (JViewport)canvas.getParent());
		} else {
			// save only the visible region of the canvas
			saved = paintComponent(canvas, file, formatName);
		}
		if (saved) {
			JOptionPane.showMessageDialog(parent,
				file.toString() + " has been saved successfully.");
		}
	}
	
	/**
	 * Prompts the user to save the entire canvas or just the visible region.
	 * If the entire canvas is selected then the canvas is removed from the viewport and resized, then
	 * painted into the image and returned to the viewport.
	 * 
	 * @return true if the image is saved
	 */
	private boolean saveViewport(File file, String formatName, PCanvas canvas, JViewport viewport) {
		boolean saved = false;

		Dimension fullSize = viewport.getViewSize();
		Point viewPosition = viewport.getViewPosition();

		// ask the user if they want an image of the whole canvas or just the visible area
		// check if the canvas is smaller than the viewport view size
		if ((fullSize.width > canvas.getWidth()) || (fullSize.height > canvas.getHeight())) {
			String msg = "Do you want to save the visible region of the canvas " + dimToString(canvas.getSize()) +
					" or the entire canvas " + dimToString(fullSize) + "?\n" +
					"Warning: saving the entire canvas can cause an out of memory error if it is too large.";
			String[] options = new String[] { " Just The Visible Region ",
										" The Entire Canvas ", " Cancel " };
			int choice = JOptionPane.showOptionDialog(viewport,
					msg, "Confirm", JOptionPane.YES_NO_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE,
					null, options, options[0]);

			if (choice == JOptionPane.CANCEL_OPTION) {
				// do nothing, false will be returned
			} else if (choice == JOptionPane.YES_OPTION) {
				// just save the visible region of the canvas
				saved = paintComponent(canvas, file, formatName);
			} else if (choice == JOptionPane.NO_OPTION) {
				// save the entire canvas, need to start in the top left corner
				viewport.setViewPosition(new Point(0, 0));

				JPanel fullPanel = new JPanel(new BorderLayout());
				fullPanel.add(canvas, BorderLayout.CENTER);
				Rectangle oldBounds = canvas.getBounds();
				// make this panel use the entire view
				fullPanel.setBounds(0, 0, fullSize.width, fullSize.height);
				// have to make the canvas use all the size
				canvas.setBounds(0, 0, fullSize.width, fullSize.height);

				saved = paintComponent(fullPanel, file, formatName);

				// restore the canvas as the original view with original position and bounds
				viewport.setView(canvas);
				viewport.setViewPosition(viewPosition);
				canvas.setBounds(oldBounds);
			}
		} else {
			// just save the visible region of the canvas
			saved = paintComponent(canvas, file, formatName);
		}
		return saved;
	}

	private boolean paintComponent(JComponent comp, File file, String formatName) {
		try {
			BufferedImage bufferedImage = new BufferedImage(comp.getWidth(), comp.getHeight(), BufferedImage.TYPE_INT_RGB);
			Graphics g = bufferedImage.getGraphics();
			comp.paint(g); // paint the canvas to the image
			ImageIO.write(bufferedImage, formatName, file);
		} catch (Throwable t) {
			// sometimes an out of memory error will occur!
			JOptionPane.showMessageDialog(parent,
					"There was a problem saving " + file.toString() +
					". The image has not been exported.\nReason: " + t.getMessage(),
					"Export Error", JOptionPane.ERROR_MESSAGE);
			t.printStackTrace();
			return false;
		}
		return true;
	}

	private static String dimToString(Dimension d) {
		return "(" + d.width + "x" + d.height + ")";
	}
}
