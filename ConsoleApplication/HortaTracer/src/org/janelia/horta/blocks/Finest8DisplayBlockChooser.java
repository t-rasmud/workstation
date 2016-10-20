/*
 * Licensed under the Janelia Farm Research Campus Software Copyright 1.1
 * 
 * Copyright (c) 2014, Howard Hughes Medical Institute, All rights reserved.
 * 
 * Redistribution and use in source and binary forms, with or without 
 * modification, are permitted provided that the following conditions are met:
 * 
 *     1. Redistributions of source code must retain the above copyright notice, 
 *        this list of conditions and the following disclaimer.
 *     2. Redistributions in binary form must reproduce the above copyright 
 *        notice, this list of conditions and the following disclaimer in the 
 *        documentation and/or other materials provided with the distribution.
 *     3. Neither the name of the Howard Hughes Medical Institute nor the names 
 *        of its contributors may be used to endorse or promote products derived 
 *        from this software without specific prior written permission.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" 
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, ANY 
 * IMPLIED WARRANTIES OF MERCHANTABILITY, NON-INFRINGEMENT, OR FITNESS FOR A 
 * PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR 
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, 
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, 
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; 
 * REASONABLE ROYALTIES; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY 
 * THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT 
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS 
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package org.janelia.horta.blocks;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import org.janelia.geometry3d.Vector3;

/**
 * Generate sorted list of up to eight max resolution blocks near current focus
 * @author brunsc
 */
public class Finest8DisplayBlockChooser 
implements BlockChooser
{
    
    /*
     Choose the eight closest maximum resolution blocks to the current focus point.
    */
    @Override
    public List<BlockTileKey> chooseBlocks(BlockTileSource source, Vector3 focus, Vector3 previousFocus) 
    {
        // Find up to eight closest blocks adjacent to focus
        BlockTileResolution resolution = source.getMaximumResolution();
        BlockTileKey centerBlock = source.getClosestTileKey(focus, resolution);
        List<BlockTileKey> result = new ArrayList<>();
        if (centerBlock == null)
            return result;
        // initially assume close blocks are at upper right front
        int dx = 1;
        int dy = 1;
        int dz = 1;
        Vector3 centroid = source.getBlockCentroid(centerBlock);
        // maybe look for close blocks at lower left rear instead
        if (centroid.getX() > focus.getX())
            dx = -1;
        if (centroid.getY() > focus.getY())
            dy = -1;
        if (centroid.getZ() > focus.getZ())
            dz = -1;
        // Loop over the 8 closest blocks
        for (int x : new int[] {0, dx}) {
            for (int y : new int[] {0, dy}) {
                for (int z : new int[] {0, dz}) {
                    BlockTileKey blockKey = source.getBlockKeyAdjacent(centerBlock, x, y, z);
                    if (blockKey != null) {
                        result.add(blockKey);
                    }
                }
            }
        }
        // Sort the blocks strictly by distance to focus
        Collections.sort(result, new BlockComparator(focus));
        return result;
    }
    
    // Sort blocks by distance from focus to block centroid
    private static class BlockComparator implements Comparator<BlockTileKey> {
        private final Vector3 focus;
        
        BlockComparator(Vector3 focus) {
            this.focus = focus;
        }
        
        @Override
        public int compare(BlockTileKey block1, BlockTileKey block2) {
            Vector3 c1 = block1.getCentroid().minus(focus);
            Vector3 c2 = block2.getCentroid().minus(focus);
            float d1 = c1.dot(c1); // distance squared
            float d2 = c2.dot(c2);
            return d1 < d2 ? -1 : d1 > d2 ? 1 : 0;
        }
        
    }
    
}
