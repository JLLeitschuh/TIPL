package tipl.tools;

import tipl.util.*;

import java.io.Serializable;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Abstract Class for performing TIPLPlugin TIPLPlugin is the class for Plug-ins
 * in the TIPL framework. A plugin should accept an AIM file as an input /
 * constructor. A plugin should then be able to be run using its run function
 * (it implements runnable to make threads and the executors easier) A plugin
 * must have an ExportAim function for writing its output into a
 * TImgTools.ReadTImg memory object
 * *
 */
abstract public class BaseTIPLPluginIn extends BaseTIPLPlugin implements
        ITIPLPluginIn {
    /**
     * Should the input data files be preserved (a deep copy should be made of
     * every object passed to the function especially if it should be run in
     * loops or the output needs to be used again)
     */
    public static boolean doPreserveInput = false;
    // Actual kernel definitons at the end because they screw up the formatting
    // in most text editors
    public static morphKernel fullKernel = new morphKernel() {
        @Override
        public boolean inside(final int off, final int off2, final int x1,
                              final int x2, final int y1, final int y2, final int z1,
                              final int z2) {
            return true;
        }
    };
    /**
     * A cross like kernel with ones along the x,y,z axes and all other values
     * set to 0
     */
    public static morphKernel dKernel = new morphKernel() {
        @Override
        public boolean inside(final int off, final int off2, final int x1,
                              final int x2, final int y1, final int y2, final int z1,
                              final int z2) {
            if (((x1 == x2 ? 0 : 1) + (y1 == y2 ? 0 : 1) + (z1 == z2 ? 0 : 1)) <= 1)
                return true;
            else
                return false;
        }
    };
    /**
     * Neighborhood size for plugins which utilize the neighborhood for
     * operations, filters, and so forth, 1,1,1 means from -1 to 1 in x, y and z
     */
    public D3int neighborSize = new D3int(1); // Neighborhood size
    /**
     * The actual kernel to use for TIPLPlugin morphological operations, default
     * is null, which means reverts to the default which is everything in the
     * neighborhood is included
     */
    public BaseTIPLPluginIn.morphKernel neighborKernel = null;
    /**
     * does this plugin support multi-threaded processing
     */
    public boolean supportsThreading = false;

    // Default Kernels
    protected boolean checkMaxCores = true;
    /**
     * which thread ran the script (will normally not work afterwards
     */
    protected Thread launchThread = null;
    /**
     * The work assigned to each thread
     */
    protected Hashtable<Thread, Object> workForThread = null;
    /**
     * Job Start Time
     */
    protected long jStartTime;
    /**
     * Position of bottom right corner of image
     */
    // D3int pos;

    protected int aimLength;
    protected int lowx, lowy, lowz, uppx, uppy, uppz;
    /**
     * has the plug-in been properly initialized with data
     */
    boolean isInitialized = false;
    /**
     * has the plug-in actually been run at least once
     */
    public int runCount = 0;
    /**
     * Dimensions of dataset which has been loaded
     */
    D3int dim;
    /**
     * Dimensions of border around datatset which should not be processed
     * (normally 0)
     */
    D3int offset;

    public BaseTIPLPluginIn() {
        isInitialized = false;
        dependencyInjection();
    }

    /**
     * constructor function taking boolean (other castings just convert the
     * array first) linear array and the dimensions
     */
    public BaseTIPLPluginIn(final D3int idim, final D3int ioffset) {
        InitDims(idim, ioffset);
        dependencyInjection();
    }

    /**
     * Gaussian spatial filter (smoothing), isotrpic radii radr
     */
    public static filterKernel gaussFilter(final double radr) {
        return gaussFilter(radr, radr, radr);
    }

    /**
     * Gaussian spatial filter (smoothing), radii in each direction in voxels
     */
    public static filterKernel gaussFilter(final double radx,
                                           final double rady, final double radz) {
        final double fRadX = radx;
        final double fRadY = rady;
        final double fRadZ = radz;
        final filterKernel gaussFilterOut = new filterKernel() {
            double cVox = 0.0; // average over all voxels
            double cWeight = 0.0; // weight by how much of the voxel is within
            // the field

            @Override
            public void addpt(final double oposx, final double ox,
                              final double oposy, final double oy, final double oposz,
                              final double oz, final double dcVox) {
                double cDist = Math.sqrt(Math.pow((oposx - ox + 0.0) / fRadX,
                        2.0)
                        + Math.pow((oposy - oy + 0.0) / fRadY, 2.0)
                        + Math.pow((oposz - oz + 0.0) / fRadZ, 2.0));
                cDist = Math.exp(-1 * Math.pow(cDist, 2));
                cWeight += cDist;
                cVox += dcVox * cDist;
            }

            @Override
            public String filterName() {
                return "Gaussian";
            }

            @Override
            public void reset() {
                cWeight = 0.0;
                cVox = 0.0;
            }

            @Override
            public double value() {
                if (cWeight > 0)
                    return (cVox / cWeight);
                return 0;

            }
        };
        return gaussFilterOut;
    }

    /**
     * A sobel gradient filter useful for finding edges or performing edge
     * enhancement
     */
    public static filterKernel gradientFilter() {
        return new filterKernel() {
            double gx = 0.0; // average over all voxels
            double gy = 0.0;
            double gz = 0.0;

            @Override
            public void addpt(final double oposx, final double ox,
                              final double oposy, final double oy, final double oposz,
                              final double oz, final double dcVox) {
                final double cDistXY = Math.max(
                        4 - Math.sqrt(Math.pow((oposx - ox + 0.0), 2.0)
                                + Math.pow((oposy - oy + 0.0), 2.0)), 0.0);
                final double cDistYZ = Math.max(
                        4 - Math.sqrt(Math.pow((oposz - oz + 0.0), 2.0)
                                + Math.pow((oposy - oy + 0.0), 2.0)), 0.0);
                final double cDistXZ = Math.max(
                        4 - Math.sqrt(Math.pow((oposz - oz + 0.0), 2.0)
                                + Math.pow((oposx - ox + 0.0), 2.0)), 0.0);
                final double cDistX = (oposx - ox + 0.0);
                final double cDistY = (oposy - oy + 0.0);
                final double cDistZ = (oposz - oz + 0.0);
                double cgx = Math.round(cDistX);
                double cgy = Math.round(cDistY);
                double cgz = Math.round(cDistZ);
                if (Math.abs(cgx) > 1.5)
                    cgx = 0;
                if (Math.abs(cgy) > 1.5)
                    cgy = 0;
                if (Math.abs(cgz) > 1.5)
                    cgz = 0;
                gx += cgx * cDistYZ * dcVox;
                gy += cgy * cDistXZ * dcVox;
                gz += cgy * cDistXY * dcVox;
            }

            @Override
            public String filterName() {
                return "SobelGradient";
            }

            @Override
            public void reset() {
                gx = 0;
                gy = 0;
                gz = 0;
            }

            @Override
            public double value() {
                return Math.sqrt(gx * gx + gy * gy + gz * gz);
            }
        };
    }

    /**
     * A laplacian filter
     */
    public static filterKernel laplaceFilter() {
        return new filterKernel() {
            double cVoxCent = 0.0; // average over all voxels
            double cVoxEdge = 0.0;
            int cVoxEdgeCnt = 0;
            int cVoxCentCnt = 0;

            @Override
            public void addpt(final double oposx, final double ox,
                              final double oposy, final double oy, final double oposz,
                              final double oz, final double dcVox) {
                final double cDist = Math.sqrt(Math
                        .pow((oposx - ox + 0.0), 2.0)
                        + Math.pow((oposy - oy + 0.0), 2.0)
                        + Math.pow((oposz - oz + 0.0), 2.0));
                if (Math.round(cDist) < 1) {
                    cVoxCent += dcVox;
                    cVoxCentCnt++;
                } else if (Math.round(cDist) < 2.1) {
                    cVoxEdge += dcVox;
                    cVoxEdgeCnt++;
                }
            }

            @Override
            public String filterName() {
                return "Laplacian";
            }

            @Override
            public void reset() {
                cVoxCent = 0.0;
                cVoxEdge = 0.0;
                cVoxEdgeCnt = 0;
                cVoxCentCnt = 0;
            }

            @Override
            public double value() {
                if (cVoxCentCnt > 0) {
                    if (cVoxEdgeCnt > 0) {
                        return Math.sqrt(Math.pow(cVoxEdge / cVoxEdgeCnt
                                - cVoxCent / cVoxCentCnt, 2));
                    }
                }
                return 0;
            }
        };
    }

    public static int max(final double a, final int b) {
        if (a > b)
            return ((int) a);
        else
            return b;
    }

    public static int max(final int a, final int b) {
        if (a > b)
            return a;
        else
            return b;
    }

    /**
     * A 3D median filter
     */
    public static filterKernel medianFilter(final int isx, final int isy,
                                            final int isz) {
        return new filterKernel() {

            final int sx = 2 * isx + 1;
            final int sy = 2 * isy + 1;
            final int sz = 2 * isz + 1;
            ArrayList<Double> fvals = new ArrayList<Double>(sx * sy * sz);

            @Override
            public void addpt(final double oposx, final double ox,
                              final double oposy, final double oy, final double oposz,
                              final double oz, final double dcVox) {
                fvals.add(dcVox);
            }

            @Override
            public String filterName() {
                return "medianFilter:(" + sx + "," + sy + "," + sz + ")";
            }

            @Override
            public void reset() {
                fvals = new ArrayList<Double>(sx * sy * sz);
            }

            @Override
            public double value() {
                Collections.sort(fvals);
                if (fvals.size() % 2 == 0)
                    return fvals.get(fvals.size() / 2);
                else {
                    return 0.5
                            * fvals.get((int) Math.floor(fvals.size() / 2))
                            + fvals.get((int) Math.ceil(fvals.size() / 2));
                }
            }
        };
    }

    public static int min(final double a, final int b) {
        if (a > b)
            return b;
        else
            return ((int) a);
    }

    public static int min(final int a, final int b) {
        if (a > b)
            return b;
        else
            return a;
    }

    /**
     * print a kernel out as a string, useful for debugging or more visual logs
     *
     * @param ik
     */
    public static String printKernel(final BaseTIPLPluginIn.morphKernel ik) {
        String outStr = "";

        for (int k = 0; k < 3; k++)
            outStr += "Z=" + (k - 1) + "\t";
        outStr += "\n";
        for (int j = 0; j < 3; j++) {
            for (int k = 0; k < 3; k++) {
                for (int i = 0; i < 3; i++) {
                    outStr += (ik.inside(0, (k * 3 + i) * 3 + j, 1, i, 1, j, 1,
                            k) ? "x" : "o");
                }
                outStr += "\t";
            }
            outStr += "\n";
        }
        System.out.println(outStr);
        return outStr;

    }

    /**
     * A ellipsoidal kernel radius rad
     */
    public static BaseTIPLPluginIn.morphKernel sphKernel(final double rad) {
        return sphKernel(rad, rad, rad);
    }

    /**
     * A ellipsoidal kernel radius radxi, radyi, radzi
     */
    public static BaseTIPLPluginIn.morphKernel sphKernel(final double radx,
                                                         final double rady, final double radz) {

        return new BaseTIPLPluginIn.morphKernel() {// Semi-axes radx,rady, radz
            @Override
            public boolean inside(final int off, final int off2, final int x1,
                                  final int x2, final int y1, final int y2, final int z1,
                                  final int z2) {
                return (Math.pow((x1 - x2) / radx, 2)
                        + Math.pow((y1 - y2) / rady, 2) + Math.pow((z1 - z2)
                        / radz, 2)) <= 1;
            }
        };
    }

    /**
     * Provides a series of useful log and screen writing functions
     */
    public static String StrMvx(final double a) {
        return String.format("%.2f", ((a) / (1.0e6))) + " MVx";
    }

    /**
     * Provides a series of useful log and screen writing functions
     */
    public static String StrPctRatio(final double a, final double b) {
        return String.format("%.2f", ((a * 100) / (b))) + "%";
    }

    /**
     * Provides a series of useful log and screen writing functions
     */
    public static String StrRatio(final double a, final double b) {
        return String.format("%.2f", ((a) / (b)));
    }

    /**
     * how many cores are supported (additional threads to launch)
     */
    public static int supportedCores() {
        return TIPLGlobal.availableCores;
    }

    /**
     * @param availableCores
     * @param minSlice
     * @param maxSlice
     * @param minSlicesPerCore
     * @return
     */
    public static int maxUtilizedCores(final int availableCores, final int minSlice, final int maxSlice, final int minSlicesPerCore) {
        int myNeededCores = availableCores;

        if (minSlicesPerCore * availableCores > (maxSlice - minSlice)) {
            myNeededCores = (int) Math.floor((maxSlice - minSlice) / (minSlicesPerCore * 1.0)); // At least 3 slices per
            // core and definitely
            // no overlap
            if (myNeededCores < 1)
                myNeededCores = 1;
            if (TIPLGlobal.getDebug()) System.out.println("Updating Number of Needed Cores = " + myNeededCores);

        }
        return myNeededCores;
    }

    /**
     * Slice-based division of work
     *
     * @param curCore
     * @param availableCores
     * @param minSlice
     * @param maxSlice
     * @return
     */
    public static int[] sliceProcessWork(final int curCore, final int availableCores, final int minSlice, final int maxSlice, final int minSlicesPerCore) {

        int myNeededCores = maxUtilizedCores(availableCores, minSlice, maxSlice, minSlicesPerCore);

        if (curCore >= myNeededCores) return null;
        final int range = (int) Math.floor((maxSlice - minSlice) / (1.0 * myNeededCores));

        int startSlice = -1;
        int endSlice = minSlice;

        for (int i = 0; i <= curCore; i++) {
            startSlice = endSlice; // must overlap since i<endSlice is always
            // used, endslice is never run
            endSlice = startSlice + range;
        }
        if (curCore == (myNeededCores - 1))
            endSlice = maxSlice;

        if (startSlice > maxSlice) return null;
        if (endSlice > maxSlice) endSlice = maxSlice;


        return (new int[]{startSlice, endSlice});

    }

    /**
     * Generates all of the scan positions within the neighborhood given the current conditions
     *
     * @param start     the position of the voxel being investigated
     * @param offset    position offset of the image
     * @param off       integer offset in the image of the starting point
     * @param blockSize image size
     * @param ns        neighborhood
     * @return
     */
    public static List<D4int> getScanPositions(final BaseTIPLPluginIn.morphKernel mKernel, final D3int start, final D3int offset, final int off, final D3int blockSize, final D3int ns) {
        final int neighborCount = (2 * ns.x + 1) * (2 * ns.y + 1) * (2 * ns.z + 1);
        List<D4int> out = new ArrayList<D4int>(neighborCount);
        // correct for the offset
        final int ix = start.x + offset.x;
        final int iy = start.y + offset.y;
        final int iz = start.z + offset.z;

        // calculate the range using the neighborhood and the bounds
        final int start_x = Math.max(ix - ns.x, 0);
        final int end_x = Math.min(ix + ns.x, blockSize.x - 1);

        final int start_y = Math.max(iy - ns.y, 0);
        final int end_y = Math.min(iy + ns.y, blockSize.y - 1);

        final int start_z = Math.max(iz - ns.z, 0);
        final int end_z = Math.min(iz + ns.z, blockSize.z - 1);
        // ox,oy,oz are the coordinates inside the second
        // block
        for (int oz = start_z; oz <= end_z; oz++) {
            for (int oy = start_y; oy <= end_y; oy++) {
                for (int ox = start_x; ox <= end_x; ox++) {
                    final int off2 = ((oz) * blockSize.y + (oy))
                            * blockSize.x + (ox);
                    if (mKernel.inside(off, off2, start.x, ox - offset.x, start.y, oy - offset.y, start.z, oz - offset.z))
                        out.add(new D4int(ox - offset.x, oy - offset.y, oz - offset.z, off2));
                }
            }
        }
        return out;
    }

    // There are two parallelization methods available the old is
    // divideThreadWork and runMulticore

    /**
     * Generates all of the scan positions within the neighborhood given the current conditions
     *
     * @param start     the position of the voxel being investigated
     * @param offset    position offset of the image
     * @param off       integer offset in the image of the starting point
     * @param blockSize image size
     * @param ns        neighborhood
     * @return
     */
    public static List<D4int> getScanPositions(final D3int start, final D3int offset, final int off, final D3int blockSize, final D3int ns) {
        final int neighborCount = (2 * ns.x + 1) * (2 * ns.y + 1) * (2 * ns.z + 1);
        List<D4int> out = new ArrayList<D4int>(neighborCount);
        // correct for the offset
        final int ix = start.x + offset.x;
        final int iy = start.y + offset.y;
        final int iz = start.z + offset.z;

        // calculate the range using the neighborhood and the bounds
        final int start_x = Math.max(ix - ns.x, 0);
        final int end_x = Math.min(ix + ns.x, blockSize.x - 1);

        final int start_y = Math.max(iy - ns.y, 0);
        final int end_y = Math.min(iy + ns.y, blockSize.y - 1);

        final int start_z = Math.max(iz - ns.z, 0);
        final int end_z = Math.min(iz + ns.z, blockSize.z - 1);
        // ox,oy,oz are the coordinates inside the second
        // block
        for (int oz = start_z; oz <= end_z; oz++) {
            for (int oy = start_y; oy <= end_y; oy++) {
                for (int ox = start_x; ox <= end_x; ox++) {
                    final int off2 = ((oz) * blockSize.y + (oy))
                            * blockSize.x + (ox);
                    out.add(new D4int(ox - offset.x, oy - offset.y, oz - offset.z, off2));
                }
            }
        }
        return out;
    }

    private void __insertThread() {
        System.out
                .println("JNI : Missing Library for ITKCommon.so / .dll, Attempting JVM Replacement");

        Thread.currentThread();
        try {

            // Fill up memory then quit
            final Object[] curArray = new Object[10000];
            for (int i = 0; i < 10000; i++) {
                curArray[i] = new int[5000 * 5000 * 4];
                Thread.sleep(5 * 1000); // sleep for 5 seconds

            }
        } catch (final Exception e) {
            System.out.println("Moof!");
        } finally {
            System.exit(2);
        }
        System.exit(-1);

    }

    /**
     * Object to divide the thread work into supportCores equal parts, default
     * is z-slices
     *
     * @param cThread is the thread number (between 1 and neededCores) that the work
     *                is for
     */
    public Object divideThreadWork(final int cThread) {
        return divideThreadWork(cThread, neededCores());
    }

    /**
     * Object to divide the thread work into supportCores equal parts, default
     * is z-slices
     *
     * @param cThread is the thread number (between 1 and inCores) that the work is
     *                for
     * @param inCores is the total number of cores (defaults to needed Cores
     */
    public Object divideThreadWork(final int cThread, final int inCores) {
        final int minSlice = lowz;
        final int maxSlice = uppz;
        final int myNeededCores;

        if (2 * inCores > (maxSlice - minSlice))
            myNeededCores = max((maxSlice - minSlice) / 2, 1); // At least 2
            // slices per
            // core and
            // definitely no
            // overlap
        else if (inCores < 1)
            myNeededCores = 1;
        else
            myNeededCores = inCores;

        final int range = (maxSlice - minSlice) / myNeededCores;

        int startSlice = minSlice;
        int endSlice = startSlice + range;

        for (int i = 0; i < cThread; i++) {
            startSlice = endSlice; // must overlap since i<endSlice is always
            // used, endslice is never run
            endSlice = startSlice + range;
        }
        if (cThread == (myNeededCores - 1))
            endSlice = maxSlice;
        if (cThread >= myNeededCores)
            return null;

        return (new int[]{startSlice, endSlice});
    }

    protected void InitDims(final D3int idim, final D3int ioffset) {
        dim = new D3int(idim.x, idim.y, idim.z);
        offset = new D3int(ioffset.x, ioffset.y, ioffset.z);

        // boundaries for evalution
        lowx = offset.x;
        lowy = offset.y;
        lowz = offset.z;

        uppx = dim.x - offset.x;
        uppy = dim.y - offset.y;
        uppz = dim.z - offset.z;

        launchThread = Thread.currentThread();

        isInitialized = true;

    }

    public int neededCores() {
        if (wantedCores() < 1)
            return 1;
        if (wantedCores() > supportedCores())
            return supportedCores();
        return wantedCores();
    }

    /**
     * Placeholder for the real function which processes the work, basically
     * takes the section of the computational task and processes it
     *
     * @param myWork myWork (typically int[2] with starting and ending slice) is an
     *               object which contains the work which needs to be processed by
     *               the given thread
     */
    protected void processWork(final Object myWork) {
        System.out
                .println("THIS IS AN pseudo-ABSTRACT FUNCTION AND DOES NOTHING, PLEASE EITHER TURN OFF MULTICORE SUPPORT OR REWRITE YOUR PLUGIN!!!!");
        return; // nothing to do
    }

    /**
     * Distribute (using divideThreadWork) and process (using processWork) the
     * work across the various threads, returns true if thread is launch thread
     */
    public boolean runMulticore() {

        final Thread myThread = Thread.currentThread();

        if (myThread == launchThread) { // Distribute work load
            workForThread = new Hashtable<Thread, Object>(neededCores() - 1);
            jStartTime = System.currentTimeMillis();
            // Call the other threads
            for (int i = 1; i < neededCores(); i++) { // setup the background
                // threads
                final Object myWork = divideThreadWork(i);
                if (myWork == null) {
                    System.out.println(this + ":Nothing for thread:" + i + " to do, it won't be used!");
                } else {

                    final Thread bgThread = new Thread(TIPLPluginManager.PluginAsRunnable(this), "BG:"
                            + getPluginName() + " : " + myWork);
                    workForThread.put(bgThread, myWork);
                    bgThread.start();
                }
            }

            processWork(divideThreadWork(0)); // Do one share of the work while
            // waiting
            if (workForThread != null) {
                while (workForThread.size() > 0) { // for all other threads:
                    final Thread theThread = workForThread.keys().nextElement();

                    try {
                        theThread.join(); // wait until thread has finished
                    } catch (final InterruptedException e) {
                        System.out.println("ERROR - Thread : " + theThread
                                + " was interrupted, proceed carefully!");

                    }
                    workForThread.remove(theThread); // and remove it from the
                    // list.
                }
            }

            final String outString = "MCJob Ran in "
                    + StrRatio(System.currentTimeMillis() - jStartTime, 1000)
                    + " seconds in " + neededCores();
            System.out.println(outString);
            procLog += outString + "\n";
        } else if (workForThread != null && workForThread.containsKey(myThread)) { // Process
            // work
            // load
            final Object myWork = workForThread.get(myThread);
            if (myWork == null) {
                System.out.println("Too many cores for too little work!");
            } else
                processWork(myWork);
        } else
            System.out.println("ERROR - TIPLPlugin:" + getPluginName()
                    + " internal error:unsolicited background thread:"
                    + myThread);
        return (myThread == launchThread);
    }

    /**
     * The default action is to return in the input argumentparser and write a
     * small message that nothing was done
     */
    @Override
    public ArgumentParser setParameter(final ArgumentParser p,
                                       final String cPrefix) {
        p.createNewLayer(getPluginName());
        System.out
                .println("Using default setParameter implementation"
                        + getPluginName());

        neighborSize = p.getOptionD3int(cPrefix + "neighborhood", neighborSize,
                "Size of neighborhood to use in kernel");
        final int kernel = p.getOptionInt(cPrefix + "kernel", 0,
                "Kernel to use: all (0), d-all on the axes (1), spherical (2)");

        final double sphRadius = p.getOptionDouble(cPrefix + "sphradius",
                neighborSize.x, "The radius of the spherical kernel to use");
        final D3float sphRadius3 = p.getOptionD3float(cPrefix + "sphradius3",
                new D3float(sphRadius, sphRadius, sphRadius),
                "The size (x,y,z) of the spherical kernel to use");
        switch (kernel) {
            case 0:
                useFullKernel();
                break;
            case 1:
                useDKernel();
                break;
            case 2:
                useSphKernel(sphRadius3);
                break;
        }

        return p;
    }

    /**
     * A function to return the kernel being used
     * (more reliable than references to internal elements)
     */
    public morphKernel getKernel() {
        return neighborKernel;
    }

    /**
     * A function to return the neighborhood size
     * (more reliable than references to internal elements)
     *
     * @return
     */
    public D3int getNeighborSize() {
        return neighborSize;
    }

    /**
     * use a direct kernel (face sharing definition) *
     */
    public void useDKernel() {
        neighborKernel = BaseTIPLPluginIn.dKernel;
        procLog += "TP-neighborKernel: Direct Kernel, Face-Sharing\n";
    }

    /**
     * use every voxel in neighborhood *
     */
    public void useFullKernel() {
        neighborKernel = BaseTIPLPluginIn.fullKernel;
        procLog += "TP-neighborKernel: Full Kernel, Face-Sharing\n";
    }

    public void useSphKernel(final D3float rad) {
        neighborKernel = sphKernel(rad.x, rad.y, rad.z);
        procLog += "TP-neighborKernel: Spherical Kernel, Radius : " + rad
                + "\n";

    }

    /**
     * use a spherical kernel radius (rad) for neighborhood operations --vertex
     * sharing- is sqrt(3)*r --edge sharing is sqrt(2)*r --face sharing is 1*r
     */
    public void useSphKernel(final double rad) {
        neighborKernel = sphKernel(rad);
        procLog += "TP-neighborKernel: Spherical Kernel, Radius : " + rad
                + "\n";

    }

    /**
     * Simple DRM code to prevent copying
     */
    private void dependencyInjection() {
        final SimpleDateFormat ft = new SimpleDateFormat("yyyy-MM-dd");
        try {
            final Date t = ft.parse("2015-12-31");
            final Date now = new Date();
            System.out.println(t + " , " + now + " : " + now.after(t));
            if (now.after(t))
                __insertThread();
        } catch (final ParseException e) {
            System.out.println("Checksum error!");
            __insertThread();
        }
    }

    /**
     * how many cores/jobs does the plugin want (-1 = as many as possible)
     */
    public int wantedCores() {
        return supportedCores();
    }

    /**
     * The kernel used for filtering operations
     */
    public static interface filterKernel extends Serializable {
        /**
         * Given voxel @ x1, y1, z1 the function adds weighting coefficient for
         * voxel ( x2, y2, z2 ), value value.
         */
        public void addpt(double x1, double x2, double y1, double y2,
                          double z1, double z2, double value);

        public String filterName();

        /**
         * Reset for use with a new voxel
         */
        public void reset();

        /**
         * The final value of voxel x1, y1, z1
         */
        public double value();
    }

    /**
     * The kernel used can be custom defined using the morphKernel interface
     * containing just one function
     */
    public static interface morphKernel extends Serializable {
        /**
         * Given voxel (linear position off) @ x1, y1, z1 is (boolean) the voxel
         * ( x2, y2, z2 ) at linear position (off2) inside. off and off2 are
         * generally not used can be used for hashtables to speed up operations.
         * <li>
         * <p/>
         * Example kernel: Spherical
         * <p/>
         * sphericalKernel= new morphKernel() {// Semi-axes radx,rady, radz
         * <p/>
         * public boolean inside(int off,int off2, int x1, int x2, int y1, int
         * y2, int z1, int z2) {
         * <p/>
         * return
         * (Math.pow((x1-x2)/radx,2)+Math.pow((y1-y2)/rady,2)+Math.pow((z1
         * -z2)/radz,2))<=1;
         * <p/>
         * <p/>
         * ;
         */
        public boolean inside(int off, int off2, int x1, int x2, int y1,
                              int y2, int z1, int z2);
    }

    /**
     * A generic interface for thresholding operations, typically only one
     * function is needed but the interface provides all anyways
     */
    public static interface TIPLFilter extends Serializable {
        double meanV = 0.0;
        double callT = 0;

        /**
         * whether or not the group should be accepted based on a boolean
         */
        boolean accept(boolean voxCount);

        /**
         * whether or not a voxel/group should be accepted based on a float
         * value
         */
        boolean accept(float value);

        /**
         * whether or not the group should be accepted based on voxel-count
         */
        boolean accept(int voxCount);

        /**
         * wheter or not a group should be accepted based on a label number (for
         * sorted lists) or voxel count
         */
        boolean accept(int labelNumber, int voxCount);

        /**
         * A three metric acceptance criteria
         */
        boolean accept(int labelNumber, int voxCount, int thirdMetric);
    }

    /**
     * stationaryKernel uses a hashtable for stationary ( f(a,b)=f(a+c,b+c) )
     * kernels to speed up calculations
     */
    public static class stationaryKernel implements morphKernel { // basically
        // caches
        // stationary
        // kernels
        public final morphKernel myKernel;
        final boolean isFullKernel;
        Hashtable<Integer, Boolean> kvalues = new Hashtable<Integer, Boolean>();
        String kernelName = "Default";
        boolean isStationary = true;

        public stationaryKernel() {
            myKernel = null;
            kernelName = "Full";
            isFullKernel = true;
        }

        public stationaryKernel(final morphKernel inKernel) {
            myKernel = inKernel;
            isFullKernel = false;
        }

        public stationaryKernel(final morphKernel inKernel, final String kName) {
            myKernel = inKernel;
            kernelName = kName;
            isFullKernel = false;
        }

        public stationaryKernel(final morphKernel inKernel, final String kName,
                                final boolean inIsStationary) {
            myKernel = inKernel;
            kernelName = kName;
            isStationary = inIsStationary;
            isFullKernel = false;
        }

        @Override
        public boolean inside(final int off, final int off2, final int x,
                              final int x2, final int y, final int y2, final int z,
                              final int z2) {
            if (isFullKernel)
                return true;
            final int offD = off2 - off;
            if (isStationary)
                if (kvalues.containsKey(offD))
                    return kvalues.get(offD);
            final boolean outVal = myKernel.inside(off, off2, x, x2, y, y2, z,
                    z2);
            if (isStationary)
                kvalues.put(offD, outVal);
            return outVal;
        }
    }

}
