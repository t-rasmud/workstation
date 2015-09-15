/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.janelia.it.workstation.cache.large_volume;

import com.sun.media.jai.codec.ByteArraySeekableStream;
import com.sun.media.jai.codec.SeekableStream;
import java.io.File;
import java.io.Serializable;
import java.net.URL;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import net.sf.ehcache.Cache;
import net.sf.ehcache.CacheManager;
import net.sf.ehcache.Element;
import net.sf.ehcache.pool.sizeof.annotations.IgnoreSizeOf;
import org.janelia.it.workstation.gui.large_volume_viewer.BlockTiffOctreeLoadAdapter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.janelia.it.workstation.gui.large_volume_viewer.compression.CompressedFileResolver;

/**
 * This class will take a neighborhood builder, and will use that to populate
 * cache, centered around the given focus.  It will return what is needed
 * by the client, as long is it is in the cache.
 * 
 * @author fosterl
 */
public class CacheFacade {
    public static final String CACHE_NAME = "CompressedTiffCache";
    public static final int GIGA = 1024*1024*1024;
    // TODO use more dynamic means of determining how many of the slabs
    // to put into memory at one time.
    
    private GeometricNeighborhoodBuilder neighborhoodBuilder;
    private GeometricNeighborhood neighborhood;
    private CachePopulator cachePopulator;
    //private CacheAccess jcs;
    private CacheManager manager;
    private final CompressedFileResolver resolver = new CompressedFileResolver();
    private CompressedFileResolver.CompressedFileNamer compressNamer;
    private Double cameraZoom;
    private double[] cameraFocus;
    private double pixelsPerSceneUnit;
    private String cacheName;
    private int totalGets;
    private int noFutureGets;
    
    private Logger log = LoggerFactory.getLogger(CacheFacade.class);
    
    /**
     * Possibly temporary: using this class to wrap use of BTOLA, and hide
     * the dependency.  If another way can be found to find the standard
     * TIFF file size, this method need not be used.
     * 
     * @param folderUrl base folder containing TIFF files.
     * @return size of a known one, as returned by BTOLA
     */
    public static int getStandardFileLength(URL folderUrl) {
        try {
            File folder = new File(folderUrl.toURI());
            return BlockTiffOctreeLoadAdapter.getStandardTiffFileSize(folder);
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }
    
    /**
     * This is the guardian around the cache implementation.  Note that
     * neither Future nor SeekableStream are serializable, so any notion of
     * using a non-memory cache is impossible without some redesign.
     * 
     * @param region unique key for cache namespace.
     * @throws Exception from called methods.
     */
    public CacheFacade(String region, int standardFileSize) throws Exception {
        // Establishing in-memory cache, declaratively.
        log.info("Creating a cache {}.", region);
        cacheName = region;
        URL url = getClass().getResource("/ehcacheCompressedTiff.xml");
        manager = CacheManager.create(url);
        cachePopulator = new CachePopulator();
        cachePopulator.setStandadFileSize(standardFileSize);
    }

    public CacheFacade(int standardFileSize) throws Exception {
        this(CACHE_NAME, standardFileSize);
    }
    
    public void close() {
        cachePopulator.close();
    }

    public boolean isInCache(String id) {
        Cache cache = manager.getCache(cacheName);
        Element cachedElement = cache.get(id);
        return (cachedElement != null);
    }

    //public void reportCacheOccupancy() {
    //    Cache cache = manager.getCache(cacheName);
    //    List keys = cache.getKeys();
    //    int requiredGb = (int)(((long)standardFileSize * (long)keys.size()) / (GIGA));
    //    log.info("--- Cache has {} keys.  {}gb required.", keys.size(), requiredGb);
    //}

    /**
     * This getter takes the name of the decompressed version of the input file.
     * 
     * @param file decompressed file's name.
     * @return stream of data, fully decompressed.
     */
    public SeekableStream get(File file) {
        // The key stored in the cache is the compressed file name.
        File compressedFile = compressNamer.getCompressedName(file);        
        return get(compressedFile.getAbsolutePath());
    }    

    /**
     * Tell if this file is not only in cache, but will have no Future.get
     * wait time.
     *
     * @param file decompressed file's name.
     * @return true -> no waiting for cache.
     */
    public boolean isReady(File file) {
        boolean rtnVal = false;
        // The key stored in the cache is the compressed file name.
        if (compressNamer == null) {
            compressNamer = resolver.getNamer(file);
        }
        File compressedFile = compressNamer.getCompressedName(file);
        Cache cache = manager.getCache(cacheName);
        final String id = compressedFile.getAbsolutePath();
        if (isInCache(id)) {
            CachableWrapper wrapper = (CachableWrapper) cache.get(id).getObjectValue();            
            Future wrappedObject = wrapper.getWrappedObject();
            rtnVal = wrappedObject == null  ||  wrappedObject.isDone();
        }
        if (! rtnVal) {
            noFutureGets ++;
            totalGets ++;
        }
        return rtnVal;
    }

    /**
     * When focus is set/changed, the neighborhood builder goes to work,
     * and the cache is populated.
     * 
     * @todo get the zoom level as number or object, into this calculation.
     * @param focus neighborhood is around this point.
     */
	public synchronized void setFocus(double[] focus) {
        log.info("Setting focus...");
        cameraFocus = focus;
        updateRegion();
	}

    /**
     * Set the zoom, but do not trigger any changes.  Allow trigger to be
     * pulled downstream.
     */
    public void setCameraZoomValue(Double zoom) {
        log.debug("Setting zoom {}....", zoom);
        this.cameraZoom = zoom;
    }
    
    public void setPixelsPerSceneUnit(double pixelsPerSceneUnit) {
        if (pixelsPerSceneUnit < 1.0) {
            pixelsPerSceneUnit = 1.0;
        }
        this.pixelsPerSceneUnit = pixelsPerSceneUnit;
    }
    
    /**
     * Change camera zoom, and trigger any cascading updates.
     */
    public void setCameraZoom(Double zoom) {
        setCameraZoomValue(zoom);
        // Force a recalculation, based on both focus and zoom.
        if (this.cameraFocus != null) {
            updateRegion();
        }
    }

    /**
     * @return the neighborhoodBuilder
     */
    public GeometricNeighborhoodBuilder getNeighborhoodBuilder() {
        return neighborhoodBuilder;
    }

    /**
     * Supply this from without, to allow for external configuration by caller.
     *     Ex: neighborhoodBuilder =
     *             new WorldExtentSphereBuilder(tileFormat, 10000);
     * 
     * @param neighborhoodBuilder the neighborhoodBuilder to set
     */
    public void setNeighborhoodBuilder(GeometricNeighborhoodBuilder neighborhoodBuilder) {
        this.neighborhoodBuilder = neighborhoodBuilder;
        
    }

    /**
     * For testing purposes.
     */
    public void dumpKeys() {
        Cache cache = manager.getCache(cacheName);
        List keys = cache.getKeys();
        System.out.println("All Keys:=-----------------------------------: " + keys.size());
        for (Object key : keys) {
            System.out.println("KEY:" + key);
        }
    }

    private void updateRegion() {
        if (calculateRegion()) {
            populateRegion();
        }
    }

    /**
     * Blocking getters. Let the cache manager worry about the threading. Most
     * likely ID is an absolute path. Also, packages a decompressed version of
     * the cached data, into a seekable stream.
     *
     * @see CacheManager#get(java.io.File) calls this.
     * @param id what to dig up.
     * @return result, after awaiting the future, or null on exception.
     */
    private SeekableStream get(final String id) {
        String keyOnly = cachePopulator.trimToOctreePath(id);
        log.info("Getting {}", keyOnly);
        totalGets++;
        SeekableStream rtnVal = null;
        try {
            Cache cache = manager.getCache(cacheName);
            CachableWrapper wrapper = (CachableWrapper) cache.get(id).getObjectValue();
            rtnVal = new ByteArraySeekableStream(wrapper.getBytes());
            log.info("Returning {}: found in cache.", keyOnly);
        } catch (InterruptedException | ExecutionException ie) {
            log.warn("Interrupted thread, while returning {}.", id);
        } catch (Exception ex) {
            log.error("Failure to resolve cached version of {}", id);
            ex.printStackTrace();
        }
        if (rtnVal == null) {
            log.warn("Ultimately returning a null value for {}.", id);
        }

        if (totalGets % 50 == 0) {
            log.info("No-future get ratio: {}/{} = {}.", noFutureGets, totalGets, ((double) noFutureGets / (double) totalGets));
        }
        return rtnVal;
    }

    private boolean calculateRegion() {
        Date start = new Date();
        boolean rtnVal = false;
        GeometricNeighborhood calculatedNeighborhood = neighborhoodBuilder.buildNeighborhood(cameraFocus, cameraZoom, pixelsPerSceneUnit);
        if (!(calculatedNeighborhood.getFiles().isEmpty()  ||  calculatedNeighborhood.equals(neighborhood))) {
            this.neighborhood = calculatedNeighborhood;
            rtnVal = true;
        }
        Date end = new Date();
        long elapsed = (end.getTime() - start.getTime());
        log.info("In calculate region for: {}ms in thread {}.", elapsed, Thread.currentThread().getName());
        return rtnVal;
    }

    private void populateRegion() {
        log.info("Repopulating on focus.  Zoom={}", cameraZoom);
        populateRegion(neighborhood);
    }
    
    private void populateRegion(GeometricNeighborhood neighborhood) {
        Date start = new Date();
        log.info("Retargeting cache at zoom {}.", cameraZoom);
        Cache cache = manager.getCache(cacheName);
        Collection<String> futureArrays = cachePopulator.retargetCache(neighborhood, cache);
        if (log.isDebugEnabled()) {
            for (String id : futureArrays) {
                log.debug("Populating {} to cache at zoom {}.", cachePopulator.trimToOctreePath(id), cameraZoom);
            }
        }
        Date end = new Date();
        log.info("In populateRegion for: {}ms.", end.getTime() - start.getTime());
    }

    /**
     * May require serializable objects.  Neither 'Future' nor its generic 
     * target will ever be serializable.  However, since our cache resides
     * at all times in memory, making the cached object transient should not 
     * be problematic.
     */
    public static class CachableWrapper implements Serializable {
        
        // The sizing of this transient, future object must be ignored,
        // because otherwise, the ObjectGraphWalker from ehcache will
        // leak into the entire JVM, signalling many warnings.
        @IgnoreSizeOf
        private transient Future<byte[]> wrappedObject;

        // Not seeing max depth exceeded problems with empty future ref.
        //private Future<String> someObjectWhichMightCauseProblems;
        
        // No max depth exceeded problem with just transient;
        //private transient String aThing = "I BET YOU THIS WILL CAUSE PROBLEMS\n";
        
        private byte[] bytes; // The actual, final data.
        public CachableWrapper(Future<byte[]> object, byte[] bytes) {
            wrappedObject = object;
            this.bytes = bytes;
        }
        
        public byte[] getBytes() throws InterruptedException, ExecutionException {
            if (getWrappedObject() != null  &&  (! getWrappedObject().isDone())) {
                getWrappedObject().get();  // All threads await...
            }
            wrappedObject = null;      // Eligible for GC
            return bytes;
        }
        
        Future<byte[]> getWrappedObject() {
            return wrappedObject;
        }
        
    }
    
    public static class NonNeighborhoodCachableWrapper extends CachableWrapper {
        public NonNeighborhoodCachableWrapper(Future<byte[]> object, byte[] bytes) {
            super(object, bytes);
        }
    }

}
