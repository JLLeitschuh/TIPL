/**
 * 
 */
package tipl.util;

import static ch.lambdaj.Lambda.DESCENDING;
import static ch.lambdaj.Lambda.filter;
import static ch.lambdaj.Lambda.having;
import static ch.lambdaj.Lambda.on;
import static ch.lambdaj.Lambda.sort;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.either;
import static org.hamcrest.Matchers.equalToIgnoringCase;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.lessThan;
import ij.IJ;
import ij.gui.GenericDialog;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import net.java.sezpoz.Index;
import net.java.sezpoz.IndexItem;
import net.java.sezpoz.Indexable;
import tipl.formats.TImgRO;

/**
 * @author mader
 *
 */
public class TIPLPluginManager {
	/**
	 * PluginInfo stores information about each plugin so that the proper one can be loaded for the given situation
	 * 
	 * @author mader
	 *
	 */
	@Target({ ElementType.TYPE, ElementType.METHOD, ElementType.FIELD })
	@Retention(RetentionPolicy.SOURCE)
	@Indexable(type = TIPLPluginFactory.class)
	public static @interface PluginInfo {
		/**
		 * The name of the plugin (VfilterScale is Filter so is FilterScale)
		 * @return
		 */
		String pluginType();
		/**
		 * short description of the plugin
		 * @return
		 */
		String desc() default "";
		/**
		 * does it operate on slices
		 * @return
		 */
		boolean sliceBased() default false;
		/**
		 * the largest image it can handle in voxels ( -1 means unlimited, or memory limited), default is the longest an array is allowed to be
		 * @return
		 */
		long maximumSize() default Integer.MAX_VALUE-1;
		/**
		 * the number of bytes used per voxel in the input image approximately, -1 means it cannot be estimated)
		 * @return
		 */
		long bytesPerVoxel() default -1;
		/**
		 * the speed rank of the plugin from 0 slowest to 10 average to 20 highest (everything else being equal the fastest plugin is taken)
		 * @return
		 */
		int speedRank() default 10;
		/**
		 * does the plugin require Spark in order to run
		 * @return
		 */
		boolean sparkBased() default false;
	}
	/**
	 * The static method to create a new TIPLPlugin 
	 * @author mader
	 *
	 */
	public static interface TIPLPluginFactory {
		public ITIPLPlugin get();
	}
	public static interface TIPLPluginFactorSmart extends TIPLPluginFactory {
		public long estimateMemory(TImgRO[] inImgs);
		public long estimateSpeed(TImgRO[] inImgs);	
	}
	private static final HashMap<PluginInfo, TIPLPluginFactory> pluginList = new HashMap<PluginInfo, TIPLPluginFactory>(); 
	/**
	 * get the named plugin from the list
	 * @param curInfo the information on the plugin
	 * @return
	 */
	public static ITIPLPlugin getPlugin(PluginInfo curInfo) {
		if(getAllPlugins().contains(curInfo)) return pluginList.get(curInfo).get();
		else throw new IllegalArgumentException("Plugin:"+curInfo.pluginType()+" with info"+curInfo+" has not yet been loaded");
	}
	/**
	 * get the best suited plugin for the given images and return an io plugin instead of standard one
	 * @param curInfo information on the plugin to get
	 * @return an instance of the plugin
	 * @throws InstantiationException
	 */
	public static ITIPLPluginIO getPluginIO(PluginInfo curInfo) {
		return (ITIPLPluginIO) getPlugin(curInfo);
	}

	/**
	 * Get a list of all the plugin factories that exist
	 * @return
	 * @throws InstantiationException
	 */
	public static List<PluginInfo> getAllPlugins() {
		if (pluginList.size()>1) return new ArrayList<PluginInfo>(pluginList.keySet());
		for (final IndexItem<PluginInfo, TIPLPluginFactory> item : Index.load(
				PluginInfo.class, TIPLPluginFactory.class)) {
			final PluginInfo bName = item.annotation();
			try {
				final TIPLPluginFactory dBlock = item.instance();
				System.out.println(bName + " loaded as: " + dBlock);
				pluginList.put(bName, dBlock);
			} catch (InstantiationException e) {
				System.err.println("Plugin: "+bName.pluginType()+" "+bName.desc()+" could not be loaded or instantiated by plugin manager!\t"+e);
				if (TIPLGlobal.getDebug()) e.printStackTrace();
			}
		}

		return new ArrayList<PluginInfo>(pluginList.keySet());
	}
	/**
	 * A list of plugins with the given type/name
	 * @param pluginType
	 * @return a hashmap of the plugins
	 * @throws InstantiationException
	 */
	public static List<PluginInfo> getPluginsNamed(final String pluginType) 
	{
		return filter(having(on(PluginInfo.class).pluginType(),equalToIgnoringCase(pluginType)),getAllPlugins());
	}
	public static List<PluginInfo> getPluginsBySize(final List<PluginInfo> inList,long voxelCount) {
		return filter(having(on(PluginInfo.class).maximumSize(),
				either(greaterThan(voxelCount)).
				or(lessThan(0L))), // either larger than the voxel count or negative 1
				inList);
	}
	/** 
	 * get the fastest plugin (by speed rank)
	 * @param pluginType
	 * @return info for a single plugin
	 */
	public static PluginInfo getFastestPlugin(final List<PluginInfo> inList) {
		List<PluginInfo> sortedPlugs=sort(inList,on(PluginInfo.class).speedRank(),DESCENDING);
		return sortedPlugs.get(0);
	}
	/** 
	 * get the lowest memory usage plugin
	 * @param pluginType
	 * @return info for a single plugin
	 */
	public static PluginInfo getLowestMemoryPlugin(final List<PluginInfo> inList) {
		List<PluginInfo> sortedPlugs=sort(inList,on(PluginInfo.class).bytesPerVoxel());
		return sortedPlugs.get(0);
	}


	protected static <T extends TImgTools.HasDimensions> PluginInfo getBestPlugin(final String pluginType,final T[] inImages) {
		//TODO get size from other images at some point
		List<PluginInfo> namedPlugins=getPluginsNamed(pluginType);
		if (namedPlugins.size()<1) throw new IllegalArgumentException("No plugins of type:"+pluginType+" have been loaded, check classpath, and annotation setup");

		String outPlugName = "Named Plugins Plugins for "+pluginType+" are: ";

		for (PluginInfo cPlug : namedPlugins) outPlugName+=","+cPlug.toString();
		if (TIPLGlobal.getDebug()) System.out.println(outPlugName);

		long imVoxCount=(long) inImages[0].getDim().prod();
		List<PluginInfo> bestPlugins=getPluginsBySize(namedPlugins,imVoxCount);
		if (bestPlugins.size()<1) throw new IllegalArgumentException("No plugins of type:"+pluginType+" can handle images sized:"+imVoxCount);


		outPlugName = "Sized Plugins (>"+imVoxCount+") for "+pluginType+" are: ";

		for (PluginInfo cPlug : bestPlugins) outPlugName+=","+cPlug.toString();
		if (TIPLGlobal.getDebug()) System.out.println(outPlugName);

		// remove spark plugins
		List<PluginInfo> noSparkPlugins=filter(
				having(on(PluginInfo.class).sparkBased(),is(false)),
				bestPlugins);
		if (noSparkPlugins.size()<1) throw new IllegalArgumentException("No plugins of type:"+pluginType+" can handle images sized:"+imVoxCount+" can run without Spark");


		outPlugName = "Available Plugins for "+pluginType+" are: ";

		for (PluginInfo cPlug : noSparkPlugins) outPlugName+=","+cPlug.toString();
		if (TIPLGlobal.getDebug()) System.out.println(outPlugName);

		return getFastestPlugin(noSparkPlugins);
	}
	/**
	 * The standard image size if none is provided
	 */
	public static D3int DEFAULT_IMAGEDIM = new D3int(1000,1000,1000);
	/**
	 * Create an empty default image with the correct size
	 */
	public static final TImgTools.HasDimensions[] getDefaultImage() {
		return new TImgTools.HasDimensions[]{
				new TImgTools.HasDimensions() {
					@Override
					public D3int getDim() {return DEFAULT_IMAGEDIM;}

					@Override
					public D3float getElSize() {return new D3float(1,1,1);}

					@Override
					public D3int getOffset() {return new D3int(0,0,0);}

					@Override
					public D3int getPos() {return new D3int(0,0,0);}

					@Override
					public String getProcLog() {return "";}

					@Override
					public float getShortScaleFactor() {return 1;}
				}};
	}
	/**
	 * get the best suited plugin for the given images
	 * @param pluginType name of the plugin
	 * @param inImages the input images
	 * @return an instance of the plugin as a standard plugin
	 */
	public static <T extends TImgTools.HasDimensions> ITIPLPlugin createBestPlugin(final String pluginType,final T[] inImages) {
		return getPlugin(getBestPlugin(pluginType,inImages));
	}
	
	/**
	 * get the best suited plugin for the given images
	 * @param pluginType name of the plugin
	 * @param inImages the input images
	 * @return an instance of the plugin as a standard plugin
	 */
	public static ITIPLPlugin createBestPlugin(final String pluginType) {
		return getPlugin(getBestPlugin(pluginType,getDefaultImage()));
	}
	
	/**
	 * get the best suited plugin for the given images
	 * @param pluginType name of the plugin
	 * @param inImages the input images
	 * @return an instance of the plugin as an io plugin
	 */
	public static <T extends TImgTools.HasDimensions> ITIPLPluginIO createBestPluginIO(final String pluginType,final T[] inImages) {
		return getPluginIO(getBestPlugin(pluginType,inImages));
	}
	
	/**
	 * get the best suited plugin for the given images
	 * @param pluginType name of the plugin
	 * @return an instance of the plugin as an io plugin
	 */
	public static ITIPLPluginIO createBestPluginIO(final String pluginType) {
		return getPluginIO(getBestPlugin(pluginType,getDefaultImage()));
	}
	
	

	/**
	 * get the fastest (or first) suited plugin for the given images
	 * @param pluginType name of the plugin
	 * @return an instance of the plugin as an io plugin
	 */
	public static ITIPLPluginIO createFirstPluginIO(final String pluginType) {	
		return getPluginIO(getFastestPlugin(getPluginsNamed(pluginType)));
	}
	/**
	 * get the fastest (or first) suited plugin for the given images
	 * @param pluginType name of the plugin
	 * @return an instance of the plugin as  plugin
	 */
	public static ITIPLPlugin createFirstPlugin(final String pluginType) {	
		return getPlugin(getFastestPlugin(getPluginsNamed(pluginType)));
	}


	/**
	 * Converts a TIPLPlugin into a runnable to it can be given to a thread (this allows run to be permanently taken out of plugin function)
	 * @param inPlug the plugin
	 * @return a runnable which runs the execute method
	 */
	public static Runnable PluginAsRunnable(final ITIPLPlugin inPlug) {
		return new Runnable() {

			@Override
			public void run() {
				inPlug.execute();

			}

		};
	}

	public static void main(String[] args) {

		String outPlugName = "Installed Plugins are: ";

		for (PluginInfo cPlug : getAllPlugins()) outPlugName+="\nType:"+cPlug.pluginType()+"\t"+cPlug.toString();
		System.out.println(TIPLPluginManager.class.getName()+" showing all plugins available");
		System.out.println(outPlugName);
	}
	/**
	 * The connection between TIPL and ImageJ plugins. It creates a general interface for using all of the plugins on imageJ images.
	 * @author mader
	 *
	 */
	public static class IJlink implements ij.plugin.PlugIn {

		@Override
		public void run(String arg0) {
			IJ.log("Starting plugin:"+this);
			GenericDialog localGenericDialog = new GenericDialog("TIPL Function to Run");
			List<PluginInfo> plugins = TIPLPluginManager.getAllPlugins();
			String[] plugNames = new String[plugins.size()];
			int i=0;
			for(PluginInfo curPlug: plugins){
				plugNames[i] = curPlug.pluginType()+":"+curPlug.toString();
				i++;
			}

			localGenericDialog.addChoice("plugin", plugNames, plugNames[0]);
			localGenericDialog.showDialog();
			if (localGenericDialog.wasCanceled())
				return;
			int n = localGenericDialog.getNextChoiceIndex();
			IJ.log(plugNames[n]+" has been selected!");


		}

	}

}