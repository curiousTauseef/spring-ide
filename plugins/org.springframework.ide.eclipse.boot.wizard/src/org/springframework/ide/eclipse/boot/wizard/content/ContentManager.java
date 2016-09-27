/*******************************************************************************
 *  Copyright (c) 2013, 2016 Pivotal Software, Inc.
 *  All rights reserved. This program and the accompanying materials
 *  are made available under the terms of the Eclipse Public License v1.0
 *  which accompanies this distribution, and is available at
 *  http://www.eclipse.org/legal/epl-v10.html
 *
 *  Contributors:
 *      Pivotal Software, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.eclipse.boot.wizard.content;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.core.runtime.Assert;
import org.eclipse.jface.operation.IRunnableContext;
import org.springframework.ide.eclipse.boot.wizard.BootWizardActivator;
import org.springframework.ide.eclipse.boot.wizard.github.auth.AuthenticatedDownloader;
import org.springframework.ide.eclipse.boot.wizard.guides.GSImportWizardModel.AllContentDownloadState;
import org.springsource.ide.eclipse.commons.frameworks.core.downloadmanager.DownloadManager;
import org.springsource.ide.eclipse.commons.frameworks.core.util.JobUtil;
import org.springsource.ide.eclipse.commons.livexp.core.LiveVariable;

/**
 * An instance of the class manages lists of content of different types. The
 * idea is to create a subclass that provides all the concrete details on how
 * different types of content are discovered, downloaded and cached on the local
 * file system.
 * <p>
 * But the infrastructure for managing/downloading the content is shared.
 *
 * @author Kris De Volder
 */
public class ContentManager {

	private final Map<Class<?>, TypedContentManager<?>> byClass = new HashMap<Class<?>, TypedContentManager<?>>();
	private final List<ContentType<?>> types = new ArrayList<ContentType<?>>();
	
	private static final LiveVariable<AllContentDownloadState> DEFAULT_DOWNLOADSTATE = new LiveVariable<>(AllContentDownloadState.NOT_STARTED);
	
	// Assign a default content state even though it may be set externally by the model that encloses the content manager. It must never be null
	private LiveVariable<AllContentDownloadState> allContentDownloadState = DEFAULT_DOWNLOADSTATE;

	public <T extends GSContent> void register(Class<T> klass, String description, ContentProvider<T> provider) {
		try {
			Assert.isLegal(!byClass.containsKey(klass), "A content provider for " + klass + " is already registered");

			allContentDownloadState.setValue(AllContentDownloadState.NOT_STARTED);
			
			ContentType<T> ctype = new ContentType<T>(klass, description);
			types.add(ctype);
			DownloadManager downloader = downloadManagerFor(klass);
			byClass.put(klass, new TypedContentManager<T>(downloader, provider));
		} catch (Throwable e) {
			BootWizardActivator.log(e);
		}
	}
	
	/**
	 * Factory method to create a DownloadManager for a given content type name
	 */
	public DownloadManager downloadManagerFor(Class<?> contentType) throws IllegalStateException, IOException {
		return new DownloadManager(new AuthenticatedDownloader(),
				new File(BootWizardActivator.getDefault().getStateLocation().toFile(), contentType.getSimpleName()))
						.clearCache();
	}
	
	public void setAllContentDownloadState(LiveVariable<AllContentDownloadState> allContentDownloadState) {
		Assert.isNotNull(allContentDownloadState, "All Content Download State tracking cannot be null.");
		this.allContentDownloadState = allContentDownloadState;
	}

	/**
	 * Fetch the content of a given type. May return null but only if no content
	 * provider has been registered for the type.
	 */
	@SuppressWarnings("unchecked")
	public <T> T[] get(Class<T> type) {
		// To guard against different callers 
		// attempting to start a download in parallel while all downloads is currently under way, always give priority to downloading all contents. 
		// In other words, if all contents are currently being downloaded, do not allow 
		// separate download of contents for a specific type.
		if (allContentDownloadState.getValue()==AllContentDownloadState.IS_DOWNLOADING) {
			return null;
		}
		TypedContentManager<T> man = (TypedContentManager<T>) byClass.get(type);
		if (man != null) {
			return man.getAll();
		}
		return null;
	}

	public ContentType<?>[] getTypes() {
		return types.toArray(new ContentType<?>[types.size()]);
	}

	public Object[] get(ContentType<?> ct) {
		if (ct != null) {
			return get(ct.getKlass());
		}
		return null;
	}

	/**
	 * Creates a content manager that contains just a single content item.
	 */
	public static <T extends GSContent> ContentManager singleton(final Class<T> type, final String description, final T item) {
		ContentManager cm = new ContentManager();
		cm.register(type, description, new ContentProvider<T>() {
			// @Override
			public T[] fetch(DownloadManager downloader) {
				@SuppressWarnings("unchecked")
				T[] array = (T[]) Array.newInstance(type, 1);
				item.setDownloader(downloader);
				array[0] = item;
				return array;
			}
		});
		return cm;
	}

	/**
	 * Download all the content in the background. Must pass a runnable context.
	 * <p/>
	 * Also see {@link #getAllContentDownloadState()} to get a live variable to register a listener and be notified
	 * when completion ends
	 * 

	 * @param runnableContext
	 *            runnable context to run in job in background. Must not be
	 *            null.
	 * @param backgroundJobLabel
	 */
	public void downloadAllContentInBackground(IRunnableContext runnableContext,
			String backgroundJobLabel) {
		
		Assert.isLegal(runnableContext != null,
				"Runnable context when prefetching content in the background must not be null.");
		
		allContentDownloadState.setValue(AllContentDownloadState.IS_DOWNLOADING);

		try {
			JobUtil.runBackgroundJobWithUIProgress((monitor) -> {
				try {
					prefetchAllContent();
				} finally {
					// Inform any listeners that completion is finished
					allContentDownloadState.setValue(AllContentDownloadState.DOWNLOAD_COMPLETED);
					// Reset the download state
					allContentDownloadState.setValue(AllContentDownloadState.NOT_STARTED);
				}
			}, runnableContext, backgroundJobLabel);
		} catch (Throwable e) {
			// On any error, be sure to reset download state
			allContentDownloadState.setValue(AllContentDownloadState.NOT_STARTED);
		}
	}
	
	public LiveVariable<AllContentDownloadState> getAllContentDownloadState() {
		return allContentDownloadState;
	}

	protected void prefetchAllContent() {

		ContentType<?>[] allTypes = getTypes();

		if (allTypes != null) {
			for (ContentType<?> type : allTypes) {
				get(type);
			}
		}
	}
}
