package tipl.ij.volviewer;

/*

 * Volume Viewer 2.01
 * 01.12.2012
 * 
 * (C) Kai Uwe Barthel
 */

import ij.IJ;
import ij.ImageJ;
//import ij.ImageJ;
import ij.ImagePlus;
import ij.Prefs;
import ij.macro.Interpreter;
import ij.plugin.PlugIn;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Insets;
//import java.awt.Toolkit;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.Arrays;
import java.util.StringTokenizer;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

import javax.swing.JFrame;

import tipl.formats.TImg;
import tipl.formats.TImgRO;
import tipl.ij.TImgToImagePlus;
import tipl.util.ArgumentParser;
import tipl.util.D3float;
import tipl.util.ITIPLPlugin;
import tipl.util.ITIPLPluginIn;
import tipl.util.SGEJob;
import tipl.util.TIPLGlobal;
import tipl.util.TIPLPluginManager;
import tipl.util.TImgTools;
import tipl.util.TypedPath;

public final class Volume_Viewer implements PlugIn, ITIPLPluginIn {
	@TIPLPluginManager.PluginInfo(pluginType = "VolumeViewer",
			desc="Full memory volume viewer",
			sliceBased=false,
			maximumSize=1024*1024*1024)
	final public static TIPLPluginManager.TIPLPluginFactory myFactory = new TIPLPluginManager.TIPLPluginFactory() {
		@Override
		public ITIPLPlugin get() {
			return new Volume_Viewer();
		}
	};
	/**
	 * my version of the code which is originally forked from 2.01
	 */
	private final static String version = "0.25"; 
	private Control control;
	private JFrame frame;

	final double[] a1_R = new double[256];
	final double[][] a2_R = new double[256][128];
	final double[][] a3_R = new double[256][128];

	Volume vol = null;
	Cube cube = null;
	LookupTable lookupTable = null;
	private Transform tr = null;
	private Transform trLight = null;

	ImagePlus imp;
	Gui gui;
	Gradient gradientLUT, gradient2, gradient3, gradient4;
	TFrgb tf_rgb = null;
	TFalpha1 tf_a1 = null;
	TFalpha2 tf_a2 = null;
	TFalpha3 tf_a3 = null;
	TFalpha4 tf_a4 = null;

	private boolean batch = false;
	protected static ImageJ ijcore = null;

	public Volume_Viewer() {
		ijcore=TIPLGlobal.getIJInstance(); // open the ImageJ window to see images and results
		// This should be created at the very beginning
		control = new Control(this);
		control.xloc = 100;
		control.yloc = 50;

	}

	protected Volume_Viewer(Volume invol,ImagePlus inImp, TImgRO inInternalImage) {
		ijcore=TIPLGlobal.getIJInstance(); // open the ImageJ window to see images and results

		// This should be created at the very beginning
		control = new Control(this);
		control.xloc = 100;
		control.yloc = 50;
		vol = invol;
		internalImage = inInternalImage;

		if (internalImage == null)
			throw new IllegalArgumentException(this
					+ ": No image has been loaded, aborting");

		imp = inImp; 
		if (imp == null || !(imp.getStackSize() > 1)) {
			IJ.showMessage("Stack required");
			return;
		}
		if (imp.getType() == ImagePlus.COLOR_RGB) // Check for RGB stack.
			control.isRGB = true;
	}

	@Override
	public boolean execute() {

		batch = true;
		assert (internalImage != null);
		run("");
		return true;
	}

	@Override
	public boolean execute(String actionToExecute)
			throws IllegalArgumentException {
		// TODO Implement Method
		throw new IllegalArgumentException(this + " is not implemented yet!");
	}

	@Override
	public boolean execute(String actionToExecute, Object objectToUse)
			throws IllegalArgumentException {
		// TODO Implement Method
		throw new IllegalArgumentException(this + " is not implemented yet!");
	}

	@Override
	public Object getInfo(String request) {
		// TODO Implement Method
		throw new IllegalArgumentException(this + " is not implemented yet!");
	}

	@Override
	public String getPluginName() {
		return "Volume_Viewer";
	}

	@Override
	public String getProcLog() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public ArgumentParser setParameter(String inp) {
		return setParameter(TIPLGlobal.activeParser(inp.split(" ")), "");
	}

	@Override
	public void setParameter(String parameterName, Object parameterValue)
			throws IllegalArgumentException {
		// TODO Auto-generated method stub

	}

	public static String join(String[] a, String delim) {
		return Arrays.asList(a).toString().replaceAll(", ", delim)
				.replaceAll("^\\[|\\]$", "");
	}

	public static String join(String[] a) {
		return join(a, ", ");
	}

	protected TypedPath snapshotPath = new TypedPath("");
	protected boolean customRange = false;
	protected String animatedVariable = "";
	protected double animatedStart = 0, animatedEnd = 1;
	protected int animatedSteps = 10;
	protected double crMin = 0, crMax = 0;

	@Override
	public ArgumentParser setParameter(ArgumentParser p, String prefix) {
		// TODO Update help descriptions
		batch = p
				.getOptionBoolean(prefix + "batch", batch, "Run in batch mode");
		customRange = p.getOptionBoolean(prefix + "usecr", customRange,
				"Use custom ranges");
		crMin = p.getOptionDouble(prefix + "crmin", crMin, "Minimum value");
		crMax = p.getOptionDouble(prefix + "crmax", crMax, "Maximum value");
		control.xloc = p.getOptionInt(prefix + "xloc", control.xloc,
				"x location");
		control.yloc = p.getOptionInt(prefix + "yloc", control.yloc,
				"y location");
		control.showTF = p.getOptionBoolean(prefix + "showTF", true,
				"Show the Transfer function");
		control.renderMode = p.getOptionInt(prefix + "renderMode",
				control.renderMode, "mode to render in :"
						+ join(Control.renderName));
		control.interpolationMode = p.getOptionInt(
				prefix + "interpolationMode", control.interpolationMode,
				" mode ot use for interpolation: "
						+ join(Control.interpolationName));
		control.backgroundColor = new Color(p.getOptionInt(prefix
				+ "backgroundColor", control.backgroundColor.getRGB(),
				"Background Color"));
		control.lutNr = p.getOptionInt(prefix + "lutNr", control.lutNr,
				"look up table number: " + join(Control.lutName));
		control.zAspect = p.getOptionDouble(prefix + "zaspect", control.zAspect,
				"z aspect ratio");
		control.sampling = p.getOptionDouble(prefix + "sampling",
				control.sampling, "sampling of image");
		
		control.dist = p.getOptionDouble(prefix + "dist", control.dist,
				"distance to slice through the sample");
		control.distWasSet = p.getOptionBoolean(prefix + "forcedist", control.distWasSet ,
				"force the distance value given (otherwise it is automatically set)");
		control.showAxes = p.getOptionBoolean(prefix + "showaxes",
				control.showAxes, "Show the axes");
		control.showSlices = p.getOptionBoolean(prefix + "showslices",
				control.showSlices, "Show the slices");
		control.showClipLines = p.getOptionBoolean(prefix + "showClipLines",
				control.showClipLines, "show the clip lines");
		control.scale = p.getOptionDouble(prefix + "scale", control.scale,
				"how much to scale the image");
		control.degreeX = p.getOptionDouble(prefix + "degreex", control.degreeX,
				"degree of rotation in x");
		control.degreeY = p.getOptionDouble(prefix + "degreey", control.degreeY,
				"degree of rotation in y");
		control.degreeZ = p.getOptionDouble(prefix + "degreez", control.degreeZ,
				"degree of rotation in z");
		control.alphaMode = p.getOptionInt(prefix + "alphamode",
				control.alphaMode, "alpha mode to use");
		control.windowWidthImageRegion = p.getOptionInt(prefix
				+ "windowWidthImageRegion", control.windowWidthImageRegion,
				"width of image region");
		control.windowWidthSlices = p.getOptionInt(
				prefix + "windowWidthSlices", control.windowWidthSlices,
				"width of slices region");
		control.windowHeight = p.getOptionInt(prefix + "windowheight",
				control.windowHeight, "window height");
		control.useLight = p.getOptionBoolean(prefix + "useLight",
				control.useLight, "use light (solid/surface rendering");
		control.ambientValue = p.getOptionDouble(prefix + "ambientvalue",
				control.ambientValue, "");
		control.diffuseValue = p.getOptionDouble(prefix + "diffusevalue",
				control.diffuseValue, "diffuse value");
		control.specularValue = p.getOptionDouble(prefix + "specularvalue",
				control.specularValue, "specular value");
		control.shineValue = p.getOptionDouble(prefix + "shinevalue",
				control.shineValue, "");
		control.objectLightValue = p.getOptionDouble(
				prefix + "objectLightValue", control.objectLightValue, "");
		control.lightRed = p.getOptionInt(prefix + "lightred",
				control.lightRed, "");
		control.lightGreen = p.getOptionInt(prefix + "lightgreen",
				control.lightGreen, "");
		control.lightBlue = p.getOptionInt(prefix + "lightblue",
				control.lightBlue, "");
		control.snapshot = p.getOptionBoolean(prefix + "snapshot",
				control.snapshot, "Take a snapshot");
		snapshotPath = p.getOptionPath(prefix + "output", snapshotPath,
				"Location to save the output image(s)");
		animatedVariable = p.getOptionString(prefix + "animatedarg",
				animatedVariable, "Argument to animate with");
		animatedStart = p.getOptionDouble(prefix + "aa_start", animatedStart,
				"Starting value for the animated argument");
		animatedEnd = p.getOptionDouble(prefix + "aa_end", animatedStart,
				"Ending value for the animated argument");
		animatedSteps = p.getOptionInt(prefix + "aa_steps", animatedSteps,
				"Number of steps for the animated argument");

		return p;
	}
	/**
	 * the command line needed to reproduce this image
	 */
	@Override
	public String toString() {
		return this.getClass().getName() + " -input=" + internalImage.getPath()
				+ " " + setParameter(TIPLGlobal.activeParser(new String[]{}),"").toString();
	}

	protected TImgRO internalImage = null;

	@Override
	public void LoadImages(TImgRO[] inImages) {
		assert (inImages.length > 0);
		assert (inImages.length < 2);
		internalImage = inImages[0];
		D3float elSize = internalImage.getElSize();
		double estXYsize = Math.sqrt(Math.pow(elSize.x,2)+Math.pow(elSize.y, 2))/Math.sqrt(2);
		double estZsize = elSize.z ;
		
	    if (control.zAspect==1) {
	    	if ((estXYsize>0) && (estZsize>0))
	    		control.zAspect=1;
	    	else 
	    		control.zAspect=estZsize/estXYsize;
	    }
	    if (Double.isNaN(control.zAspect))
	        control.zAspect=1;
	    
		load_image();
	}

	public static void main(String args[]) {
		ArgumentParser cArgs = TIPLGlobal.activeParser(args);
		Volume_Viewer vv = new Volume_Viewer();
		
		TypedPath inpath = cArgs.getOptionPath("input", "", "Image to be opened");
		
		vv.setParameter(cArgs, "");
		boolean runAsJob = cArgs.getOptionBoolean("sge:runasjob",
						"Run this script as an SGE job (adds additional settings to this task");
		SGEJob jobToRun=null;
		if (runAsJob) jobToRun = SGEJob.runAsJob(Volume_Viewer.class.getName(), cArgs, "sge:");
		
		cArgs.checkForInvalid();
		if (runAsJob) jobToRun.submit();
		else {
			TImg inData = TImgTools.ReadTImg(inpath);
			vv.LoadImages(new TImgRO[] { inData });
			vv.run("");
			vv.waitForClose();
		}
	}

	/**
	 * standard access from tipl based tools
	 * 
	 * @param args
	 * @param inTImg
	 *            TImgRO to be rendered
	 */
	public void tiplShowView(TImgRO inTImg) {
		LoadImages(new TImgRO[] { inTImg });
		run("");
		waitForClose();
	}

	protected Future<Volume> fVol;

	protected void load_image() {
		if (internalImage == null)
			throw new IllegalArgumentException(this
					+ ": No image has been loaded, aborting");

		imp = TImgToImagePlus.MakeImagePlus(internalImage);
		if (imp == null || !(imp.getStackSize() > 1)) {
			IJ.showMessage("Stack required");
			return;
		}
		if (imp.getType() == ImagePlus.COLOR_RGB) // Check for RGB stack.
			control.isRGB = true;
		ExecutorService myPool = TIPLGlobal.requestSimpleES(1);
		final Volume_Viewer cVV = this;
		if (customRange) {
			fVol = myPool.submit(new Callable<Volume>() {
				public Volume call() {
					return Volume.create(control, cVV,cVV.internalImage, crMin, crMax);
				}
			});
		} else {
			fVol = myPool.submit(new Callable<Volume>() {
				public Volume call() {
					return Volume.create(control, cVV,cVV.internalImage);
				}
			});
		}
		myPool.shutdown();
	}

	/**
	 * run the plugin with a starting image
	 * 
	 * @param args
	 *            commands for plugin
	 * @param inImp
	 *            the image to use
	 */
	public void run(String args) {
		// make sure all of the data is loaded
		try {
			vol = fVol.get();
		} catch (Exception e) {
			e.printStackTrace();
			throw new IllegalArgumentException(this
					+ " volume loading crashed:" + e.getMessage());
		}
		if (animatedVariable.length() > 0) {
			batch = true; // no sense in making a whole crap ton of windows
			double curValue = animatedStart;
			double stepSize = (animatedEnd - animatedStart)
					/ (animatedSteps - 1);
			TypedPath rootName = snapshotPath;
			String cAnimatedArgument = "-" + animatedVariable + "=";
			ExecutorService myPool = TIPLGlobal.requestSimpleES();
			final Volume finalVol = vol;
			final TImgRO itImg = internalImage;
			final ImagePlus fimp=TImgToImagePlus.MakeImagePlus(internalImage);
			
			for (int i = 0; i < animatedSteps; i++) {
				ArgumentParser p = setParameter(cAnimatedArgument + curValue
						+ " -output=" + rootName.getPath() + "_"
						+ String.format("%04d", i) + ".tiff");
				p.checkForInvalid();
				final String finalArgs = p.toString();
				
				myPool.submit(new Runnable() {
					public void run() {
						Volume_Viewer cPlug = new Volume_Viewer(finalVol,fimp,itImg);
						cPlug.setParameter(finalArgs);
						System.out.println("Now executing:"+finalArgs);
						cPlug.run_plugin();
					}

				});
				curValue += stepSize;
			}
			TIPLGlobal.waitForever(myPool);
			//flush_plugin();
			//cleanup();
		} else
			run_plugin();
		
		
	}

	protected void flush_plugin() {
		lookupTable = null;
		cube = null;
		tr = null;
		trLight = null;
		gui = null;
		TIPLGlobal.runGC();
	}

	public void run_plugin() {
		lookupTable = new LookupTable(control, this);
		
		lookupTable.readLut();

		cube = new Cube(control, vol.widthV, vol.heightV, vol.depthV);
		cube.setSlicePositions(control.positionFactorX,
				control.positionFactorY, control.positionFactorZ,
				control.zAspect);

		tr = new Transform(control, control.windowWidthImageRegion,
				control.windowHeight, vol.xOffa, vol.yOffa, vol.zOffa);
		tr.setScale(control.scale);
		tr.setZAspect(control.zAspect);
		setRotation(control.degreeX, control.degreeY, control.degreeZ);
		initializeTransformation();
		cube.setTransform(tr);
		cube.setTextPositions(control.scale, control.zAspect);
		trLight = new Transform(control, -1, -1, 0, 0, 0);
		trLight.initializeTransformation();
		
		gradientLUT = new Gradient(control, this, 256, 18);
		gui = new Gui(control, this);
		gui.makeGui();
		gui.newDisplayMode();
		
		lookupTable.loadLut(control.lutNr);
		lookupTable.setLut();

		if (Interpreter.isBatchMode())
			batch = true;

		if (batch) {
			do {
				try {
					Thread.sleep(50);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			} while (!control.isReady);
			if (snapshotPath.length() > 0)
				gui.imageRegion.saveToImageFile(snapshotPath.getPath(), this.toString());
			else
				gui.imageRegion.saveToImage();

		} else {
			frame = new JFrame("3D Preview " + version + " ");
			frame.setLocation(control.xloc, control.yloc);
			frame.addWindowListener(new WindowAdapter() {
				public void windowClosing(WindowEvent e) {
					if (control.snapshot)
						gui.imageRegion.saveToImage();

					cleanup();
					frame.dispose();
				}
			});
			frame.setVisible(true);
			frame.getContentPane().add(gui);
			gui.requestFocus();
			frame.pack();
			frame.validate();

			// add component/resize listener
			frame.addComponentListener(new ComponentAdapter() {
				public void componentResized(ComponentEvent event) {
					buildFrame();
				}
			});
		}
	}

	/**
	 * wait for the frame to close
	 */
	public void waitForClose() {
		TImgToImagePlus.waitForFrameClose(frame);
		cleanup();
	}

	void reset() {
		tf_rgb = null;
		tf_a1 = null;
		tf_a2 = null;
		tf_a3 = null;
		tf_a4 = null;
		control.reset();

		buildFrame();
	}

	void buildFrame() {
		Insets insets = frame.getInsets();
		int ww = frame.getWidth() - insets.left - insets.right;
		int wh = frame.getHeight() - insets.bottom - insets.top;

		int h = wh
				- (gui.upperButtonPanel.getHeight() + gui.lowerButtonPanel
						.getHeight());
		if (h < control.windowMinHeight)
			h = control.windowMinHeight;
		Dimension dim = gui.picSlice.getSliceViewSize((int) (0.25 * ww),
				h - 130);
		int wl = dim.width;
		int transferPanelWidth = (control.showTF) ? gui.transferFunctionPanel
				.getPreferredSize().width : 0;
				int wr = ww
						- (wl + control.windowWidthSliderRegion + transferPanelWidth);
				if (wr < 480) {
					int diff = 480 - wr;
					wr = 480;
					wl -= diff;
					if (wl < 200)
						wl = 200;
				}

				if (control.windowHeight > 0 && ww > 0) {
					control.windowHeight = h;
					control.windowWidthSlices = wl;
					control.windowWidthImageRegion = wr;

					frame.getContentPane().remove(gui);

					tr = new Transform(control, control.windowWidthImageRegion,
							control.windowHeight, vol.xOffa, vol.yOffa, vol.zOffa);
					tr.setScale(control.scale);
					tr.setZAspect(control.zAspect);
					setRotation(control.degreeX, control.degreeY, control.degreeZ);
					initializeTransformation();
					cube.setTransform(tr);

					gui = new Gui(control, this);
					gui.makeGui();
					frame.getContentPane().add(gui);
					frame.pack();
					gui.requestFocus();
					gui.newDisplayMode();
				}
	}

	/*
	 * just remove all the variables by setting them to null and 'force' a
	 * garbage collect
	 */
	private void cleanup() {
		
		vol = null;

		gui.pic = null;
		gui.picSlice = null;
		gui = null;

		cube = null;
		lookupTable = null;
		tr = null;
		trLight = null;

		imp = null;
		gradientLUT = gradient2 = gradient3 = gradient4 = null;
		tf_rgb = null;
		tf_a1 = null;
		tf_a2 = null;
		tf_a3 = null;
		tf_a4 = null;

		control = null;

		TIPLGlobal.runGC();
	}

	private void readPrefsOLD() {

		control.xloc = (int) Prefs.get("VolumeViewer.xloc", 100);
		control.yloc = (int) Prefs.get("VolumeViewer.yloc", 50);
		control.showTF = Prefs.get("VolumeViewer.showTF", true);

		control.renderMode = (int) Prefs.get("VolumeViewer.renderMode",
				control.renderMode);
		control.interpolationMode = (int) Prefs.get(
				"VolumeViewer.interpolationMode", control.interpolationMode);
		control.backgroundColor = new Color((int) Prefs.get(
				"VolumeViewer.backgroundColor",
				control.backgroundColor.getRGB()));
		control.lutNr = (int) Prefs.get("VolumeViewer.lutNr", control.lutNr);
		// control.zAspect = (double) Prefs.get("VolumeViewer.zAspect",
		// control.zAspect);
		control.sampling = (double) Prefs.get("VolumeViewer.sampling",
				control.sampling);
		control.dist = (double) Prefs.get("VolumeViewer.dist", control.dist);
		control.showAxes = Prefs.get("VolumeViewer.showAxes", control.showAxes);
		control.showSlices = Prefs.get("VolumeViewer.showSlices",
				control.showSlices);
		control.showClipLines = Prefs.get("VolumeViewer.showClipLines",
				control.showClipLines);
		control.scale = (double) Prefs.get("VolumeViewer.scale", control.scale);
		control.degreeX = (double) Prefs.get("VolumeViewer.degreeX",
				control.degreeX);
		control.degreeY = (double) Prefs.get("VolumeViewer.degreeY",
				control.degreeY);
		control.degreeZ = (double) Prefs.get("VolumeViewer.degreeZ",
				control.degreeZ);
		control.alphaMode = (int) Prefs.get("VolumeViewer.alphaMode",
				control.alphaMode);
		control.windowWidthImageRegion = (int) Prefs.get(
				"VolumeViewer.windowWidthImageRegion",
				control.windowWidthImageRegion);
		control.windowWidthSlices = (int) Prefs.get(
				"VolumeViewer.windowWidthSlices", control.windowWidthSlices);
		control.windowHeight = (int) Prefs.get("VolumeViewer.windowHeight",
				control.windowHeight);
		control.useLight = Prefs.get("VolumeViewer.useLight", control.useLight);
		control.ambientValue = (double) Prefs.get("VolumeViewer.ambientValue",
				control.ambientValue);
		control.diffuseValue = (double) Prefs.get("VolumeViewer.diffuseValue",
				control.diffuseValue);
		control.specularValue = (double) Prefs.get("VolumeViewer.specularValue",
				control.specularValue);
		control.shineValue = (double) Prefs.get("VolumeViewer.shineValue",
				control.shineValue);
		control.objectLightValue = (double) Prefs.get(
				"VolumeViewer.objectLightValue", control.objectLightValue);
		control.lightRed = (int) Prefs.get("VolumeViewer.lightRed",
				control.lightRed);
		control.lightGreen = (int) Prefs.get("VolumeViewer.lightGreen",
				control.lightGreen);
		control.lightBlue = (int) Prefs.get("VolumeViewer.lightBlue",
				control.lightBlue);
	}

	private void writePrefsOLD() {
		Prefs.set("VolumeViewer.xloc", frame.getLocation().x);
		Prefs.set("VolumeViewer.yloc", frame.getLocation().y);
		Prefs.set("VolumeViewer.showTF", true);

		Prefs.set("VolumeViewer.renderMode", control.renderMode);
		Prefs.set("VolumeViewer.interpolationMode", control.interpolationMode);
		Prefs.set("VolumeViewer.backgroundColor",
				control.backgroundColor.getRGB());
		Prefs.set("VolumeViewer.lutNr", control.lutNr);
		// Prefs.set("VolumeViewer.zAspect", control.zAspect);
		Prefs.set("VolumeViewer.sampling", control.sampling);
		Prefs.set("VolumeViewer.dist", control.dist);
		Prefs.set("VolumeViewer.showAxes", control.showAxes);
		Prefs.set("VolumeViewer.showSlices", control.showSlices);
		Prefs.set("VolumeViewer.showClipLines", control.showClipLines);
		Prefs.set("VolumeViewer.scale", control.scale);
		Prefs.set("VolumeViewer.degreeX", control.degreeX);
		Prefs.set("VolumeViewer.degreeY", control.degreeY);
		Prefs.set("VolumeViewer.degreeZ", control.degreeZ);
		Prefs.set("VolumeViewer.alphaMode", control.alphaMode);
		Prefs.set("VolumeViewer.windowWidthImageRegion",
				control.windowWidthImageRegion);
		Prefs.set("VolumeViewer.windowWidthSlices", control.windowWidthSlices);
		Prefs.set("VolumeViewer.windowHeight", control.windowHeight);
		Prefs.set("VolumeViewer.useLight", control.useLight);
		Prefs.set("VolumeViewer.ambientValue", control.ambientValue);
		Prefs.set("VolumeViewer.diffuseValue", control.diffuseValue);
		Prefs.set("VolumeViewer.specularValue", control.specularValue);
		Prefs.set("VolumeViewer.shineValue", control.shineValue);
		Prefs.set("VolumeViewer.objectLightValue", control.objectLightValue);
		Prefs.set("VolumeViewer.lightRed", control.lightRed);
		Prefs.set("VolumeViewer.lightGreen", control.lightGreen);
		Prefs.set("VolumeViewer.lightBlue", control.lightBlue);
	}

	void setRotation(double degreeX, double degreeY, double degreeZ) {
		tr.setView(Math.toRadians(degreeX), Math.toRadians(degreeY),
				Math.toRadians(degreeZ));
		updateGuiSpinners();
	}

	void initializeTransformation() {
		tr.initializeTransformation();
		cube.transformCorners(tr);
		updateGuiSpinners();
	}

	void setScale() {
		tr.setScale(control.scale);
	}

	void changeRotation(int xStart, int yStart, int xAct, int yAct, int width) {
		tr.setMouseMovement(xStart, yStart, xAct, yAct, width);
		updateGuiSpinners();
	}

	void changeRotationLight(int xStart, int yStart, int xAct, int yAct,
			int width) {
		trLight.setMouseMovement(xStart, yStart, xAct, yAct, width);
	}

	void setZAspect() {
		gui.updateDistSlider();
		tr.setZAspect(control.zAspect);
		if (control.zAspect <=0.01f)
			control.zAspect = 0.01f;
	    if (Double.isNaN(control.zAspect))
	        control.zAspect=1;

		cube.setTextPositions(control.scale, control.zAspect);
	}

	void changeTranslation(int dx, int dy) {
		tr.setMouseMovementOffset(dx, dy);
		updateGuiSpinners();
	}

	void updateGuiSpinners() {

		control.degreeX = tr.getDegreeX();
		control.degreeY = tr.getDegreeY();
		control.degreeZ = tr.getDegreeZ();

		if (!control.spinnersAreChanging && gui != null)
			gui.setSpinners();

		cube.transformCorners(tr);
	}

	private boolean getMacroParameters(String st) { // read macro parameters
		String[] paramStrings = { "display_mode=", "interpolation=", "bg_r=",
				"bg_g=", "bg_b=", "lut=", "z-aspect=", "sampling=", "dist=",
				"axes=", "slices=", "clipping", "scale=", "angle_x=",
				"angle_y=", "angle_z=", "alphamode=", "width=", "height=",
				"useLight=", "ambientValue=", "diffuseValue=",
				"specularValue=", "shineValue=", "objectLightValue=",
				"lightRed=", "lightGreen=", "lightBlue=", "snapshot=" };

		double[] paramVals = { control.renderMode, control.interpolationMode,
				control.backgroundColor.getRed(),
				control.backgroundColor.getGreen(),
				control.backgroundColor.getBlue(), control.lutNr,
				control.zAspect, control.sampling, control.dist,
				(control.showAxes == true) ? 1 : 0,
						(control.showSlices == true) ? 1 : 0,
								(control.showClipLines == true) ? 1 : 0, control.scale,
										control.degreeX, control.degreeY, control.degreeZ,
										control.alphaMode, control.windowWidthImageRegion,
										control.windowHeight, (control.useLight == true) ? 1 : 0,
												control.ambientValue, control.diffuseValue,
												control.specularValue, control.shineValue,
												control.objectLightValue, control.lightRed, control.lightGreen,
												control.lightBlue, (control.snapshot == true) ? 1 : 0 };
		boolean distWasSet = false;
		try {
			if (st != null) {
				StringTokenizer ex1; // Declare StringTokenizer Objects
				ex1 = new StringTokenizer(st); // Split on Space (default)

				String str;
				while (ex1.hasMoreTokens()) {
					str = ex1.nextToken();
					boolean valid = false;
					for (int j = 0; j < paramStrings.length; j++) {
						String pattern = paramStrings[j];
						if (str.lastIndexOf(pattern) > -1) {
							int pos = str.lastIndexOf(pattern)
									+ pattern.length();
							paramVals[j] = Double.parseDouble(str.substring(pos));
							valid = true;
							if (j == 8)
								distWasSet = true;
						}
					}
					if (!valid) {
						IJ.error("Unkown macro parameter for the VolumeViewer plugin:\n"
								+ " \n"
								+ str
								+ " \n"
								+ " \n"
								+ "Valid parameters are: defaultValue type (range) \n"
								+ "display_mode=0 	int (0 .. 4)\n"
								+ "interpolation=1	int (0 .. 3)\n"
								+ "bg_r=0  bg_g=52  bg_b=101	int int (0 .. 255)\n"
								+ "lut=0				int (0 .. 4)\n"
								+ "z-aspect=1 	double  (!= 0)\n"
								+ "sampling=1		double ( > 0) \n"
								+ "dist=0			double\n"
								+ "axes=1			int (0,1)\n"
								+ "slices=0		int (0,1)\n"
								+ "clipping=0		int (0,1)\n"
								+ "scale=1		double (> 0.25, < 128) \n"
								+ "angle_x=115  angle_y=41  angle_z=17 	double (0 .. 360)\n"
								+ "alphamode=0	int (0 .. 3)\n"
								+ "width=500		int (>= 500)\n"
								+ "height=660		int (>= 630)\n"
								+ "useLight=0		int (0,1)\n"
								+ "ambientValue=0.5	double (0 .. 1)\n"
								+ "diffuseValue=0.5	double (0 .. 1)\n"
								+ "specularValue=0.5	double (0 .. 1)\n"
								+ "shineValue=17		double (0 .. 200)\n"
								+ "objectLightValue=0.5	double (0 .. 2)\n"
								+ "lightRed=255  lightGreen=128  lightBlue=0	int (0 .. 255)\n"
								+ "snapshot=0		int (0,1)");
						return false;
					}
				}
			}
		} catch (NumberFormatException e1) {
			IJ.error("Error in macro parameter list");
			return false;
		}

		control = new Control(this);
		control.distWasSet = distWasSet;
		control.renderMode = (int) Math.min(4, Math.max(0, paramVals[0]));
		control.interpolationMode = (int) Math
				.min(3, Math.max(0, paramVals[1]));
		control.backgroundColor = new Color((int) paramVals[2],
				(int) paramVals[3], (int) paramVals[4]);
		control.lutNr = (int) Math.min(4, Math.max(0, paramVals[5]));
		control.zAspect = paramVals[6];
		if (Double.isNaN(control.zAspect))
	        control.zAspect=1;
		control.sampling = (paramVals[7] > 0) ? paramVals[7] : 1;
		control.dist = paramVals[8];
		control.showAxes = ((int) paramVals[9] == 0) ? false : true;
		control.showSlices = ((int) paramVals[10] == 0) ? false : true;
		control.showClipLines = ((int) paramVals[11] == 0) ? false : true;
		control.scale = Math.max(0.25f, Math.min(128, paramVals[12]));
		control.degreeX = (int) paramVals[13];
		control.degreeY = (int) paramVals[14];
		control.degreeZ = (int) paramVals[15];
		control.alphaMode = (int) paramVals[16];
		control.windowWidthImageRegion = Math.max(
				control.windowWidthImageRegion, (int) paramVals[17]);
		control.windowHeight = Math.max(control.windowHeight,
				(int) paramVals[18]);
		control.useLight = ((int) paramVals[19] == 0) ? false : true;
		control.ambientValue = Math.max(0f, Math.min(1f, paramVals[20]));
		control.diffuseValue = Math.max(0f, Math.min(1f, paramVals[21]));
		control.specularValue = Math.max(0f, Math.min(1f, paramVals[22]));
		control.shineValue = Math.max(0f, Math.min(200f, paramVals[23]));
		control.objectLightValue = Math.max(0f, Math.min(200f, paramVals[24]));
		control.lightRed = (int) Math.max(0, Math.min(255, paramVals[25]));
		control.lightGreen = (int) Math.max(0, Math.min(255, paramVals[26]));
		control.lightBlue = (int) Math.max(0, Math.min(255, paramVals[27]));
		control.snapshot = ((int) paramVals[28] == 0) ? false : true;

		control.scaledDist = control.dist * control.scale;

		return true;
	}

	public double[] trScreen2Vol(double xS, double yS, double zS) {
		return tr.trScreen2Vol(xS, yS, zS);
	}

	public double[] trScreen2Volume(double[] xyzS) {
		return tr.trScreen2Vol(xyzS[0], xyzS[1], xyzS[2]);
	}

	public double[] trVolume2Screen(double[] xyzV) {
		return tr.trVol2Screen(xyzV[0], xyzV[1], xyzV[2]);
	}

	public double[] trVolume2Screen(double xV, double yV, double zV) {
		return tr.trVol2Screen(xV, yV, zV);
	}

	public double[] trLightScreen2Vol(double xS, double yS, double zS) {
		return trLight.trScreen2Vol(xS, yS, zS);
	}

	public double[] trLightVolume2Screen(double xV, double yV, double zV) {
		return trLight.trVol2Screen(xV, yV, zV);
	}

}
