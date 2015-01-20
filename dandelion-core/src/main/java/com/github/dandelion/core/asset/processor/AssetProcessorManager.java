/*
 * [The "BSD licence"]
 * Copyright (c) 2013-2014 Dandelion
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 * 1. Redistributions of source code must retain the above copyright
 * notice, this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright
 * notice, this list of conditions and the following disclaimer in the
 * documentation and/or other materials provided with the distribution.
 * 3. Neither the name of Dandelion nor the names of its contributors
 * may be used to endorse or promote products derived from this software
 * without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE AUTHOR ``AS IS'' AND ANY EXPRESS OR
 * IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
 * OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED.
 * IN NO EVENT SHALL THE AUTHOR BE LIABLE FOR ANY DIRECT, INDIRECT,
 * INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT
 * NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 * THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF
 * THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.github.dandelion.core.asset.processor;

import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.Writer;
import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import javax.servlet.http.HttpServletRequest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.dandelion.core.Context;
import com.github.dandelion.core.asset.Asset;
import com.github.dandelion.core.asset.AssetType;
import com.github.dandelion.core.utils.AssetUtils;

/**
 * <p>
 * System in charge of discovering and manipulating all implementations of
 * {@link AssetProcessor} available in the classpath.
 * </p>
 * 
 * @author Thibault Duchateau
 * @author Romain Lespinasse
 * @since 0.10.0
 */
public final class AssetProcessorManager {

	private static final Logger LOG = LoggerFactory.getLogger(AssetProcessorManager.class);

	/**
	 * The Dandelion context.
	 */
	private final Context context;

	public AssetProcessorManager(Context context) {
		this.context = context;
	}

	public Set<Asset> process(Set<Asset> assets, HttpServletRequest request) {

		if (context.getActiveProcessors().isEmpty()) {
			return assets;
		}

		// Process only non-vendor assets
		for (Asset asset : AssetUtils.filtersNotVendor(assets)) {

			List<AssetProcessor> compatibleAssetProcessors = getCompatibleProcessorFor(asset);

			if (!compatibleAssetProcessors.isEmpty()) {

				// Context to be passed in asset processors
				ProcessingContext processingContext = new ProcessingContext(context, asset, request);

				String content = context.getCacheManager().getContent(asset.getCacheKey());

				Reader assetReader = new StringReader(content);
				Writer assetWriter = new StringWriter();

				for (AssetProcessor assetProcessor : compatibleAssetProcessors) {
					LOG.trace("Applying processor {} on {}", assetProcessor.getProcessorKey(), asset.toLog());
					assetWriter = new StringWriter();
					assetProcessor.process(assetReader, assetWriter, processingContext);
					assetReader = new StringReader(assetWriter.toString());
				}

				// The old asset is removed from the cache
				// TODO: ideally, it should not have been put in even once
				context.getCacheManager().remove(asset.getCacheKey());

				String newCacheKey = this.context.getCacheManager().generateMinCacheKey(request, asset);
				asset.setCacheKey(newCacheKey);

				if (!asset.isVendor()
						&& (this.context.getConfiguration().isAssetCachingEnabled() || this.context.getConfiguration()
								.isAssetMinificationEnabled())) {
					asset.setFinalLocation(AssetUtils.getAssetFinalLocation(request, asset, "min"));
				}

				// The cache system is updated with the new key/content pair
				context.getCacheManager().storeContent(newCacheKey, assetWriter.toString());
			}
			else {
				LOG.trace("No compatible processor was found for the asset {}", asset.toLog());
			}
		}

		return assets;
	}

	private List<AssetProcessor> getCompatibleProcessorFor(Asset asset) {

		List<AssetProcessor> compatibleProcessors = new ArrayList<AssetProcessor>();

		for (AssetProcessor assetProcessor : context.getActiveProcessors()) {
			Annotation annotation = assetProcessor.getClass().getAnnotation(CompatibleAssetType.class);
			CompatibleAssetType compatibleAssetType = (CompatibleAssetType) annotation;
			List<AssetType> compatibleAssetTypes = Arrays.asList(compatibleAssetType.types());
			if (compatibleAssetTypes.contains(asset.getType())) {
				compatibleProcessors.add(assetProcessor);
			}
		}

		return compatibleProcessors;
	}
}
