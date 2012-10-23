/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2012, Red Hat, Inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */

package org.jboss.as.patching.runner;

import org.jboss.as.patching.PatchInfo;
import org.jboss.as.patching.PatchLogger;
import org.jboss.as.patching.PatchMessages;
import org.jboss.as.patching.metadata.BundleItem;
import org.jboss.as.patching.metadata.ContentModification;
import org.jboss.as.patching.metadata.ModificationType;

import java.io.File;
import java.io.IOException;

/**
 * @author Emanuel Muckenhuber
 */
class BundlePatchingTask extends AbstractPatchingTask<BundleItem> {

    public BundlePatchingTask(PatchingTaskDescription description) {
        super(description, BundleItem.class);
    }

    @Override
    byte[] backup(PatchingContext context) throws IOException {
        // Check the bundle dir hash
        final PatchInfo patchInfo = context.getPatchInfo();
        final File[] repoRoots = patchInfo.getBundlePath();
        final String bundleName = contentItem.getName();
        final String slot = contentItem.getSlot();
        for(final File path : repoRoots) {
            // Check the bundle path
            final File bundlePath = PatchContentLoader.getModulePath(path, bundleName, slot);
            if(bundlePath.exists()) {
                PatchLogger.ROOT_LOGGER.debugf("found in path (%s)", bundlePath.getAbsolutePath());
                // Bundles don't contain a modules.xml
                final File[] children = bundlePath.listFiles();
                if(children == null || children.length == 0) {
                    return NO_CONTENT;
                }
                return PatchUtils.calculateHash(bundlePath);
            }
        }
        return NO_CONTENT;
    }

    @Override
    byte[] apply(PatchingContext context, PatchContentLoader loader) throws IOException {
        // Copy the new bundle resources to the patching directory
        final File targetDir = context.getModulePatchDirectory(contentItem);
        final File sourceDir = loader.getFile(contentItem);
        final File[] moduleResources = sourceDir.listFiles();
        if(! targetDir.mkdirs() && ! targetDir.exists()) {
            throw PatchMessages.MESSAGES.cannotCreateDirectory(targetDir.getAbsolutePath());
        }
        if(moduleResources == null || moduleResources.length == 0) {
            return NO_CONTENT;
        }
        for(final File file : moduleResources) {
            final File target = new File(targetDir, file.getName());
            PatchUtils.copy(file, target);
        }
        return contentItem.getContentHash();
    }

    @Override
    ContentModification createRollbackEntry(ContentModification original, byte[] targetHash, byte[] itemHash) {
        final BundleItem item = new BundleItem(contentItem.getName(), contentItem.getSlot(), itemHash);
        switch (original.getType()) {
            case ADD:
                return new ContentModification(item, targetHash, ModificationType.REMOVE);
            case REMOVE:
                return new ContentModification(item, targetHash, ModificationType.ADD);
            default:
                return new ContentModification(item, targetHash, ModificationType.MODIFY);
        }
    }

}
