/*******************************************************************************
 * Copyright (c) 2014, 2016 GoPivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     GoPivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.eclipse.boot.core.initializr;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLConnection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.osgi.framework.Version;
import org.osgi.framework.VersionRange;
import org.springframework.ide.eclipse.boot.util.Log;
import org.springsource.ide.eclipse.commons.frameworks.core.downloadmanager.URLConnectionFactory;

/**
 * This class is the 'parsed' form of the json metadata for spring intializr service.
 *
 * See: https://github.com/spring-io/initializr/wiki/Initializr-metadata-json-format
 *
 * @author Kris De Volder
 */
public class InitializrServiceSpec {

	private JSONObject data;
	public static final String JSON_CONTENT_TYPE_HEADER = "application/vnd.initializr.v2.1+json";

	public InitializrServiceSpec(JSONObject jsonObject) {
		this.data = jsonObject;
	}

	public static InitializrServiceSpec parseFrom(URLConnectionFactory urlConnectionFactory, URL url) throws IOException, Exception {
		URLConnection conn = null;
		InputStream input = null;
		try {
			conn = urlConnectionFactory.createConnection(url);
			conn.addRequestProperty("Accept", JSON_CONTENT_TYPE_HEADER);
			conn.connect();
			input = conn.getInputStream();
			return parseFrom(input);
		} finally {
			if (input!=null) {
				try {
					input.close();
				} catch (IOException e) {
				}
			}
		}
	}

	public static InitializrServiceSpec parseFrom(InputStream input) throws Exception {
		return new InitializrServiceSpec(new JSONObject(new JSONTokener(new InputStreamReader(input, "utf8"))));
	}

	/////////////////////////////////////////////////

	public static abstract class Nameable {
		private String name;

		public String getName() {
			return name;
		}

		public void setName(String name) {
			this.name = name;
		}
	}

	public static class Type extends Option {
		private String action;

		public void setAction(String action) {
			this.action = action;
		}

		public String getAction() {
			return action;
		}
	}

	public static class Dependency extends Nameable implements IdAble{

		private String id;
		private String description;
		private String versionRange;

		public String getId() {
			return id;
		}

		@Override
		public String getName() {
			String name = super.getName();
			return name!=null?name:getId();
		}

		public void setId(String id) {
			this.id = id;
		}

		public String getDescription() {
			return description;
		}

		public void setDescription(String description) {
			this.description = description;
		}

		public static Dependency[] from(JSONArray values) throws JSONException {
			Dependency[] deps = new Dependency[values.length()];
			for (int i = 0; i < deps.length; i++) {
				JSONObject obj = values.getJSONObject(i);
				deps[i] = new Dependency();
				deps[i].setId(obj.getString("id"));
				deps[i].setName(obj.optString("name"));
				deps[i].setDescription(obj.optString("description"));
				deps[i].setVersionRange(obj.optString("versionRange"));
			}
			return deps;
		}

		public void setVersionRange(String range) {
			this.versionRange = range;
		}

		public String getVersionRange() {
			return versionRange;
		}

		@Override
		public String toString() {
			return "Dep("+id+","+description+","+versionRange+")";
		}

		public boolean isSupportedFor(String bootVersion) {
			try {
				if (StringUtils.isNotBlank(versionRange)) {
					final VersionRange range = new VersionRange(versionRange);
					String versionStr = bootVersion;
					if (versionStr!=null) {
						Version version = new Version(versionStr.replace("BUILD-SNAPSHOT", "ZZZZZZZZZZZZZ"));
						//replacement of BS -> ZZ: see bug https://www.pivotaltracker.com/story/show/100963226
						return range.includes(version);
					}
				}
			} catch (Exception e) {
				Log.log(e);
			}
			return true;
		}
	}

	public static class DependencyGroup extends Nameable {

		private Dependency[] content;

		public Dependency[] getContent() {
			return content;
		}

		public void setContent(Dependency[] content) {
			this.content = content;
		}
	}

	public static class Option extends Nameable {
		private String id;

		public String getId() {
			return id;
		}

		public void setId(String id) {
			this.id = id;
		}

		private boolean isDefault;

		public boolean isDefault() {
			return isDefault;
		}

		public void setDefault(boolean isDefault) {
			this.isDefault = isDefault;
		}
	}


	/////////////////////////////////////////////////////////////////

	public Map<String, String> getTextInputs() throws JSONException {
		Map<String,String> defaults = new HashMap<>();
		Iterator<String> props = data.keys();
		while(props.hasNext()) {
			String key = props.next();
			JSONObject obj = data.getJSONObject(key);
			String type = obj.optString("type");
			if ("text".equals(type)) {
				defaults.put(key, obj.optString("default", ""));
			}
		}
		return defaults;
	}

	public Type[] getTypeOptions(String groupName) {
		try {
			JSONObject obj = data.optJSONObject(groupName);
			if (obj!=null && "action".equals(obj.optString("type"))) {
				String defaultValue = obj.optString("default", "");
				JSONArray arr = obj.getJSONArray("values");
				Type[] options = new Type[arr.length()];
				for (int i = 0; i < options.length; i++) {
					JSONObject option = arr.getJSONObject(i);
					options[i] = new Type();
					String id = option.getString("id");
					String name = option.getString("name");
					String action = option.getString("action");
					options[i].setId(id);
					options[i].setName(name);
					options[i].setAction(action);
					options[i].setDefault(id.equals(defaultValue));
				}
				return options;
			}
		} catch (JSONException e) {
			//ignore
		}
		return new Type[0];
	}


	public Option[] getSingleSelectOptions(String groupName) {
		try {
			JSONObject obj = data.optJSONObject(groupName);
			if (obj!=null && "single-select".equals(obj.optString("type"))) {
				String defaultValue = obj.optString("default", "");
				JSONArray arr = obj.getJSONArray("values");
				Option[] options = new Option[arr.length()];
				for (int i = 0; i < options.length; i++) {
					JSONObject option = arr.getJSONObject(i);
					options[i] = new Option();
					String id = option.getString("id");
					String name = option.getString("name");
					options[i].setId(id);
					options[i].setName(name);
					options[i].setDefault(id.equals(defaultValue));
				}
				return options;
			}
		} catch (JSONException e) {
			//ignore
		}
		return new Option[0];
	}

	public DependencyGroup[] getDependencies() {
		return getHierarchicalMultiSelect("dependencies");
	}

	private DependencyGroup[] getHierarchicalMultiSelect(String prop) {
		try {
			JSONObject obj = data.optJSONObject(prop);
			if (obj!=null && "hierarchical-multi-select".equals(obj.optString("type"))) {
				JSONArray arr = obj.getJSONArray("values");
				DependencyGroup[] groups = new DependencyGroup[arr.length()];
				for (int i = 0; i < groups.length; i++) {
					JSONObject group = arr.getJSONObject(i);
					groups[i] = new DependencyGroup();
					String name = group.getString("name");
					JSONArray values = group.getJSONArray("values");
					groups[i].setName(name);
					groups[i].setContent(Dependency.from(values));
				}
				return groups;
			}
		} catch (JSONException e) {
			//ignore
		}
		return new DependencyGroup[0];
	}

}
