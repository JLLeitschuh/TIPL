/**
 * 
 */
package tipl.blocks;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.HashMap;
import java.util.LinkedHashMap;

import net.java.sezpoz.Index;
import net.java.sezpoz.IndexItem;
import net.java.sezpoz.Indexable;
import tipl.formats.TImg;
import tipl.formats.TImgRO;
import tipl.tools.Resize;
import tipl.util.ArgumentDialog;
import tipl.util.ArgumentList;
import tipl.util.ArgumentParser;
import tipl.util.D3int;
import tipl.util.SGEJob;
import tipl.util.TIPLGlobal;
import tipl.util.TImgTools;

/**
 * A basic concrete implementation of TIPLBlock with the helper functions
 * Handles checking for files and determining ready status
 * Can be launched from either this script or the blockrunner tool
 * 
 * @author mader
 * 
 */
public abstract class BaseTIPLBlock implements ITIPLBlock {
	
	@Target({ ElementType.TYPE, ElementType.METHOD, ElementType.FIELD })
	@Retention(RetentionPolicy.SOURCE)
	@Indexable(type = TIPLBlockFactory.class)
	public static @interface BlockIdentity {
		String blockName();
		String desc() default "";
		String[] inputNames();
		String[] outputNames();
		
	}
	/**
	 * The static method to create a new TIPLBlock 
	 * @author mader
	 *
	 */
	public static abstract interface TIPLBlockFactory {
		public ITIPLBlock get();
	}
	/**
	 * Get a list of all the block factories that exist
	 * @return
	 * @throws InstantiationException
	 */
	public static HashMap<BlockIdentity, TIPLBlockFactory> getAllBlockFactories()
			throws InstantiationException {
		
		final HashMap<BlockIdentity, TIPLBlockFactory> current = new HashMap<BlockIdentity, TIPLBlockFactory>();

		for (final IndexItem<BlockIdentity, TIPLBlockFactory> item : Index.load(
				BlockIdentity.class, TIPLBlockFactory.class)) {
			final BlockIdentity bName = item.annotation();
			final TIPLBlockFactory dBlock = item.instance();
			System.out.println(bName + " loaded as: " + dBlock);
			current.put(bName, dBlock);
		}
		return current;
	}
	
	protected ITIPLBlock[] prereqBlocks = new ITIPLBlock[] {};
	protected ArgumentParser args = TIPLGlobal.activeParser(new String[] {});
	protected String blockName = "";
	protected boolean skipBlock = true;
	protected boolean saveToCache = false;
	protected boolean readFromCache = true;
	/**
	 * maximum number of slices to read in (-1 is unlimited)
	 */
	protected int maxReadSlices=-1; 
	protected LinkedHashMap<String, String> blockConnections = new LinkedHashMap<String, String>();
	final protected LinkedHashMap<String, ArgumentList.TypedPath> ioParameters = new LinkedHashMap<String, ArgumentList.TypedPath>();
	public final static String kVer = "140828_005";

	protected static void checkHelp(final ArgumentParser p) {
		if (p.hasOption("?")) {
			System.out.println(" BlockRunner");
			System.out.println(" Runs TIPLBlocks and parse arguments");
			System.out.println(" Arguments::");
			System.out.println(" ");
			System.out.println(p.getHelp());
			System.exit(0);
		}
		p.checkForInvalid();
	}
	
	/**
	 * A runnable block which takes the block itself as an argument so it can read parameters and other information from the block while running
	 * It also internally reads parameters so clumsy hacks need not always be made
	 * 
	 * @author mader
	 *
	 */
	public static interface BlockRunnable extends ArgumentParser.IsetParameter {
		/**
		 * the run command itself
		 * @param inBlockID the block being run
		 */
		public void run(BaseTIPLBlock inBlockID);
		
	}
	
	/**
	 * Just like runnable but allows an addition setup step
	 * @author mader
	 *
	 */
	public static interface ConfigurableBlockRunnable extends BlockRunnable {
		/**
		 * in case any setup needs to be run before the execution starts
		 * useful for reading in parameters and other assorted tasks
		 * @param inBlockID the block being run (typical use is to then grab the parameter list)
		 */
		public void setup(BaseTIPLBlock inBlockID);
	}
	
	
	/**
	 * Create a block from a few parameters and a runnable
	 * 
	 * @param name
	 * @param prefix
	 * @param earlierPathArgs
	 * @param outArgs
	 * @param job the command to actually be run (type blockrunnable or configurableblockrunnable)
	 * @param parFunc
	 * @return TIPLBlock to be run later
	 */
	public static ITIPLBlock InlineBlock(final String name, final String prefix,
			final String[] earlierPathArgs, final String[] outArgs,
			final BlockRunnable job) {
		return InlineBlock(name, prefix, new ITIPLBlock[] {}, earlierPathArgs,
				outArgs, job);
	}

	/**
	 * Create a block inline by defining the code (job : runnable) and the
	 * inputs (parFunc) before hand
	 * 
	 * @param name
	 * @param prefix
	 * @param earlierBlocks
	 * @param earlierPathArgs
	 *            needed input arguments
	 * @param outputArgs
	 *            needed output arguments
	 * @param job the command to actually be run (type blockrunnable or configurableblockrunnable)
	 * @param parFunc
	 * @return TIPLBlock to be run later
	 */
	public static ITIPLBlock InlineBlock(final String name, final String prefix,
			final ITIPLBlock[] earlierBlocks, final String[] earlierPathArgs,
			final String[] outputArgs, final BlockRunnable job) {
		final ITIPLBlock cBlock = new BaseTIPLBlock(name, earlierBlocks) {
			protected String cPrefix=prefix;
			@Override
			protected IBlockImage[] bGetInputNames() {
				final IBlockImage[] inNames = new BlockImage[earlierPathArgs.length];
				for (int i = 0; i < earlierPathArgs.length; i++) {
					inNames[i] = new BlockImage(earlierPathArgs[i],
							"No description provided", true);
				}
				return inNames;
			}

			@Override
			protected IBlockImage[] bGetOutputNames() {
				final IBlockImage[] outNames = new BlockImage[outputArgs.length];
				for (int i = 0; i < outputArgs.length; i++) {
					outNames[i] = new BlockImage(outputArgs[i],
							"No description provided", true);
				}
				return outNames;
			}

			@Override
			public boolean executeBlock() {
				if (job instanceof ConfigurableBlockRunnable) ((ConfigurableBlockRunnable) job).setup(this);
				if (!isReady()) {
					System.out.println("Block is not ready!");
					return false;
				}
				try {
					job.run(this);
					return true;
				} catch (final Exception e) {
					System.out.println("Execution of block has failed!");
					e.printStackTrace();
				}
				return true;
			}

			@Override
			public String getDescription() {
				return "InlineBlocks normally don't get fancy names";
			}

			@Override
			public String getPrefix() {
				return cPrefix;
			}
			@Override
			public void setPrefix(String setValue) {
				cPrefix=setValue;
			}

			@Override
			public ArgumentParser setParameterBlock(final ArgumentParser p) {
				ArgumentParser outPar = job.setParameter(p, prefix);
				
				return outPar;
			}

		};
		return cBlock;
	}

	public static void main(final String[] args) {

		System.out.println("BlockRunner v" + kVer);
		System.out.println("Runs a block by its name");
		System.out.println(" By Kevin Mader (kevin.mader@gmail.com)");
		
		ArgumentParser p = TIPLGlobal.activeParser(args);
		final String blockname = p.getOptionString("blockname", "",
				"Class name of the block to run");
		final boolean withGui = p.getOptionBoolean("gui",false,"Show a GUI for parameter adjustment");
		
		// black magic
		if (blockname.length() > 0) {
			ITIPLBlock cBlock = null;
			try {
				cBlock = (ITIPLBlock) Class.forName(blockname).newInstance();
			} catch (final ClassNotFoundException e) {
				// Try adding the tipl.blocks to the beginning
				try {
					cBlock = (ITIPLBlock) Class.forName("tipl.blocks."+blockname).newInstance();
				} catch (final ClassNotFoundException e2) {
					e.printStackTrace();
					throw new IllegalArgumentException("Block Class:" + blockname
							+ " was not found, does it exist?");
				} catch (final Exception e2) {
					e.printStackTrace();
					throw new IllegalArgumentException("Block Class:" + blockname
							+ " could not be created, sorry!");
				}
				
			} catch (final Exception e) {
				e.printStackTrace();
				throw new IllegalArgumentException("Block Class:" + blockname
						+ " could not be created, sorry!");
			}
			if (withGui) {
				p = ArgumentDialog.GUIBlock(cBlock,p.subArguments("gui",true));
			} 
			
			p = cBlock.setParameter(p);
			
			
			// code to enable running as a job
			final boolean runAsJob = p
					.getOptionBoolean("sge:runasjob",
							"Run this script as an SGE job (adds additional settings to this task");
			SGEJob jobToRun = null;
			if (runAsJob)
				jobToRun = SGEJob.runAsJob(BaseTIPLBlock.class.getName(), p.subArguments("gui",true),
						"sge:");

			checkHelp(p);

			final boolean isBlockReady = cBlock.isReady();
			if (isBlockReady) {
				if (runAsJob) {
					jobToRun.submit();
				} else {
					cBlock.execute();
				}

			}

		} else
			checkHelp(p);

	}
	public static final boolean readImageDuringTry=false;
	/**
	 * Attempts to load the aim file with the given name (usually tif stack) and
	 * returns whether or not something has gone wrong during this loading
	 * 
	 * @param filename
	 *            Path and name of the file/directory to open
	 */
	public static boolean tryOpenImagePath(final ArgumentList.TypedPath filename) {

		TImg tempAim = null; // TImg (should be, but currently that eats way too much computer time)
		if (filename.length() > 0) {
			System.out.println("Trying- to open ... " + filename);
		} else {
			System.out
					.println("Filename is empty, assuming that it is not essential and proceeding carefully!! ... ");
			return true;
		}

		try {
			System.out.println("Trying-Image Found: "+filename+(readImageDuringTry ? " and will be open..." : " will be assumed to be ok!"));
			if (!readImageDuringTry) return true;
			
			tempAim = TImgTools.ReadTImg(filename); // ReadTImg (should be, but currently that eats way too much computer time)
			System.out.println("Trying-Image Opened, checking dimensions: "+tempAim.getDim());
			if (tempAim.getDim().prod() < 1)
				return false;
			return (tempAim.isGood());
		} catch (final Exception e) {
			tempAim = null;
			TIPLGlobal.runGC();
			return false;
		}

	}

	public BaseTIPLBlock(final String inName) {
		blockName = inName;
	}

	public BaseTIPLBlock(final String inName, final ITIPLBlock[] earlierBlocks) {
		blockName = inName;
		prereqBlocks = earlierBlocks;
	}
	
	@Override
	public double memoryFactor() {
		System.err.println("Defaulting to "+this+" probably not what you wanted");
		return 2;
	}
	@Override
	public long neededMemory() {
		System.err.println("Defaulting to "+this+" probably not what you wanted");
		return 22*1024; //22 gigabytes 
	}

	protected abstract IBlockImage[] bGetInputNames();

	protected abstract IBlockImage[] bGetOutputNames();

	@Override
	public void connectInput(final String inputName,
			final ITIPLBlock outputBlock, final String outputName) {
		blockConnections.put(inputName, outputBlock.getPrefix() + outputName);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see tipl.util.TIPLBlock#execute()
	 */
	@Override
	final public boolean execute() {
		if (!skipBlock) {
			System.out.println(toString()+(isReady() ? " is ready!" : " is not ready!!!!"));
			return executeBlock();
		} else {
			System.out.println(toString() + " skipped!");
		}
		return true;
	}
	
	/**
	 * Local block code for running
	 * 
	 * @return success
	 */
	public abstract boolean executeBlock();

	protected abstract String getDescription();

	@Override
	@Deprecated
	public ArgumentList.TypedPath getFileParameter(final String argument) {
		return ioParameters.get(argument);
	}
	
	private TImgRO getInputFileRaw(final String argument) {
		 if (getFileParameter(argument).length()<1) {
			 System.out.println("Block:"+this.blockName+" is missing file argument:"+argument);
			 throw new IllegalArgumentException("Block:"+this.blockName+" is missing file argument:"+argument);
			 //return null;
		 }
		 return TImgTools.ReadTImg(getFileParameter(argument),readFromCache,saveToCache);
	}
	protected int lastReadSlices=-2;
	protected int startReadSlices=-1;
	/**
	 * only reads in a limited number of slices (enables quick and dirty script tests and easily dividing data sets to speed up analysis)
	 * @param argument name of commandline argument to read in
	 * @param startSlice first slice to take from the image
	 * @param endSlice last slice to take
	 * @return cropped (if needed) version of the file
	 */
	private TImgRO getInputFileSliceRange(final String argument,int startSlice, int endSlice) {
		TImgRO fullImage=getInputFileRaw(argument);
		Resize myResize=new Resize(fullImage);
		D3int outPos=fullImage.getPos();
		D3int outDim=fullImage.getDim();
		myResize.cutROI(new D3int(outPos.x,outPos.y,Math.max(outPos.x, startSlice)),new D3int(outDim.x,outDim.y,Math.min(outDim.z-1, endSlice-startSlice)));
		myResize.execute();
		return myResize.ExportImages(fullImage)[0];
	}

	@Override
	public void setSliceRange(int startSlice,int finishSlice) {
		startReadSlices=startSlice;
		lastReadSlices=finishSlice;
	}
	/**
	 * get the current range 
	 * @return
	 */
	public int[] getSliceRange() {
		return new int[] {startReadSlices,lastReadSlices};
	}
	
	@Override
	public TImgRO getInputFile(final String argument) {
		if (lastReadSlices>=startReadSlices) return getInputFileSliceRange(argument,startReadSlices,lastReadSlices);
		else return getInputFileRaw(argument);
	}

	@Override
	public IBlockInfo getInfo() {
		return new IBlockInfo() {
			@Override
			public String getDesc() {
				return getDescription();
			}

			@Override
			public IBlockImage[] getInputNames() {
				return bGetInputNames();
			}

			@Override
			public IBlockImage[] getOutputNames() {
				return bGetOutputNames();
			}

		};
	}

	@Override
	public boolean isComplete() {
		// TODO Auto-generated method stub
		return false;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see tipl.util.TIPLBlock#isReady()
	 */
	@Override
	public boolean isReady() {
		// TODO Auto-generated method stub
		boolean retValue = true;
		for (final ITIPLBlock cblock : prereqBlocks)
			if (!cblock.isComplete()) {
				System.out.println("Not ready for block " + toString()
						+ ", block:" + cblock + " has not completed");
				retValue = false;
			}
		for (final IBlockImage cImage : getInfo().getInputNames()) {
			if (cImage.isEssential()) {
				final String carg = getPrefix() + cImage.getName(); // create
																	// argument
																	// from info
				if (args.hasOption(carg)) {
					final String curFile = args.getOptionAsString(carg);
					if (!tryOpenImagePath(curFile)) {
						System.out.println("Not ready for block " + toString()
								+ ", file:" + carg + "=" + curFile
								+ " cannot be found / loaded");
						retValue = false;
					}
				} else {
					System.out.println("Not ready for block " + toString()
							+ ", argument:" + carg
							+ " cannot be found / loaded");
					retValue = false;
				}
			}
		}
		return retValue;
	}

	@Override
	final public ArgumentParser setParameter(ArgumentParser p) { // prevent
																	// overrriding
		// p.blockOverwrite();
		skipBlock = p.getOptionBoolean(getPrefix() + "skipblock",
				"Skip this block");
		// Process File Inputs
		final IBlockInfo aboutMe = getInfo();
		for (final IBlockImage cImage : aboutMe.getInputNames()) {

			if (blockConnections.containsKey(cImage.getName()))
				p.forceMatchingValues(blockConnections.get(cImage.getName()),
						getPrefix() + cImage.getName());
			// otherwise treat it like a normal argument
			final ArgumentList.TypedPath oValue = p.getOptionPath(
					getPrefix() + cImage.getName(), cImage.getDefaultValue(),
					cImage.getDesc()
							+ ((cImage.isEssential()) ? ", Needed"
									: ", Optional"));
			ioParameters.put(cImage.getName(), oValue);
		}
		for (final IBlockImage cImage : aboutMe.getOutputNames()) {
			if (blockConnections.containsKey(cImage.getName()))
				p.forceMatchingValues(blockConnections.get(cImage.getName()),
						getPrefix() + cImage.getName());

			// otherwise treat it like a normal argument
			final ArgumentList.TypedPath oValue = p.getOptionPath(
					getPrefix() + cImage.getName(), cImage.getDefaultValue(),
					cImage.getDesc()
							+ ((cImage.isEssential()) ? ", Needed"
									: ", Optional"));
			ioParameters.put(cImage.getName(), oValue);
		}
		// Process Standard Inputs
		p = setParameterBlock(p);
		args = p;
		return p;
	}

	/*
	 * Note: setParameter sets the args to the result of setParameterBlock
	 * (non-Javadoc)
	 * 
	 * @see tipl.util.TIPLBlock#setParameters(tipl.util.ArgumentParser)
	 */
	public abstract ArgumentParser setParameterBlock(ArgumentParser p);

	@Override
	public String toString() {
		return "BK:" + blockName;
	}
	/**
	 * Background saving makes things faster but can cause issues
	 */
	final static protected boolean bgSave=false;
	/**
	 * A save function so background saving can be controlled on a block level
	 * @param imgObj
	 */
	public void SaveImage(TImgRO imgObj,String nameArg) {
		if(bgSave) {
			TImgTools.WriteBackground(imgObj, getFileParameter(nameArg));
		} else {
			TImgTools.WriteTImg(imgObj, getFileParameter(nameArg), saveToCache);
		}
	}

}
