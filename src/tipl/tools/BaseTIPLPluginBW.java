package tipl.tools;

import tipl.formats.TImg;
import tipl.formats.TImgRO;
import tipl.util.D3int;
import tipl.util.TImgTools;

// Used as a replacement for the moment function as it allows much more control over data
// and communication with webservices (potentially?)
/**
 * Class for plugins with binary input and binary output operations on Aim
 * (linear array) files. Provides interfaces for casting inputs in other formats
 * to binary
 */
abstract public class BaseTIPLPluginBW extends BaseTIPLPluginIO {
	/** First input aim */
	public boolean[] inAim;

	/** Output aim */
	public volatile boolean[] outAim;

	public BaseTIPLPluginBW() {
		isInitialized = false;
	}

	/** initializer function taking an aim-file */
	@Override
	public TImg ExportAim(TImgRO.CanExport templateAim) {
		if (isInitialized) {
			if (runCount > 0) {
				final TImg outAimData = templateAim.inheritedAim(outAim, dim,
						offset);
				outAimData.appendProcLog(procLog);
				return outAimData;
			} else {
				System.err
						.println("The plug-in : "
								+ getPluginName()
								+ ", has not yet been run, exported does not exactly make sense, original data will be sent.");
				return templateAim.inheritedAim(inAim, dim, offset);
			}
		} else {
			System.err
					.println("The plug-in : "
							+ getPluginName()
							+ ", has not yet been initialized, exported does not make any sense");
			return templateAim.inheritedAim(templateAim);

		}
	}

	/**
	 * initializer function taking boolean (other castings just convert the
	 * array first) linear array and the dimensions
	 */
	@Deprecated
	public void ImportAim(boolean[] inputmap, D3int idim, D3int ioffset) {
		aimLength = inputmap.length;
		inAim = new boolean[aimLength];
		for (int i = 0; i < aimLength; i++)
			inAim[i] = inputmap[i];
		InitLabels(idim, ioffset);
	}

	/**
	 * initializer function taking a float (thresheld>0) linear array and the
	 * dimensions
	 */
	@Deprecated
	public void ImportAim(float[] inputmap, D3int idim, D3int ioffset) {
		aimLength = inputmap.length;
		inAim = new boolean[aimLength];
		for (int i = 0; i < aimLength; i++)
			inAim[i] = inputmap[i] > 0;
		InitLabels(idim, ioffset);
	}

	/**
	 * initializer function taking int (thresheld>0) linear array and the
	 * dimensions
	 */
	@Deprecated
	public void ImportAim(int[] inputmap, D3int idim, D3int ioffset) {
		aimLength = inputmap.length;
		inAim = new boolean[aimLength];
		for (int i = 0; i < aimLength; i++)
			inAim[i] = inputmap[i] > 0;
		InitLabels(idim, ioffset);
	}

	/**
	 * initializer function taking short (thresheld>0) linear array and the
	 * dimensions
	 */
	@Deprecated
	public void ImportAim(short[] inputmap, D3int idim, D3int ioffset) {
		aimLength = inputmap.length;
		inAim = new boolean[aimLength];
		for (int i = 0; i < aimLength; i++)
			inAim[i] = inputmap[i] > 0;
		InitLabels(idim, ioffset);
	}

	/** initializer function taking an aim-file */
	public void ImportAim(TImgRO inImg) {
		ImportAim(TImgTools.makeTImgFullReadable(inImg).getBoolAim(),
				inImg.getDim(), inImg.getOffset());
	}

	protected void InitLabels(D3int idim, D3int ioffset) {
		outAim = new boolean[aimLength];
		InitDims(idim, ioffset);
		isInitialized = true;
	}

	@Override
	public void LoadImages(TImgRO[] inImages) {
		// TODO Auto-generated method stub
		if (inImages.length < 1)
			throw new IllegalArgumentException(
					"Too few arguments for LoadImages in:" + getPluginName());
		final TImgRO inImg = inImages[0];
		ImportAim(inImg);
	}

	@Override
	abstract public void run();

}
