/*******************************************************************************
 * Copyright (c) 2016 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.eclipse.cloudfoundry.manifest.editor;

import java.util.Collection;
import java.util.Set;

import javax.inject.Provider;

import org.springframework.ide.eclipse.editor.support.hover.DescriptionProviders;
import org.springframework.ide.eclipse.editor.support.util.HtmlSnippet;
import org.springframework.ide.eclipse.editor.support.yaml.schema.YType;
import org.springframework.ide.eclipse.editor.support.yaml.schema.YTypeFactory;
import org.springframework.ide.eclipse.editor.support.yaml.schema.YTypeFactory.YAtomicType;
import org.springframework.ide.eclipse.editor.support.yaml.schema.YTypeFactory.YBeanType;
import org.springframework.ide.eclipse.editor.support.yaml.schema.YTypeFactory.YTypedPropertyImpl;
import org.springframework.ide.eclipse.editor.support.yaml.schema.YTypeUtil;
import org.springframework.ide.eclipse.editor.support.yaml.schema.YValueHint;
import org.springframework.ide.eclipse.editor.support.yaml.schema.YamlSchema;

import com.google.common.collect.ImmutableSet;

/**
 * @author Kris De Volder
 */
public class ManifestYmlSchema implements YamlSchema {

	private final YBeanType TOPLEVEL_TYPE;
	private final YTypeUtil TYPE_UTIL;
	private final Provider<Collection<YValueHint>> buildpackProvider;

	private static final Set<String> TOPLEVEL_EXCLUDED = ImmutableSet.of(
		"name", "host", "hosts"
	);

	public ManifestYmlSchema(Provider<Collection<YValueHint>> buildpackProvider) {
		this.buildpackProvider = buildpackProvider;
		YTypeFactory f = new YTypeFactory();
		TYPE_UTIL = f.TYPE_UTIL;

		// define schema types
		TOPLEVEL_TYPE = f.ybean("manifest.yml schema");

		YBeanType application = f.ybean("Application");
		YAtomicType t_path = f.yatomic("Path");

		YAtomicType t_buildpack = f.yatomic("Buildpack");

		t_buildpack.addHintProvider(this.buildpackProvider);

		YAtomicType t_boolean = f.yenum("boolean", "true", "false");
		YType t_string = f.yatomic("String");
		YType t_strings = f.yseq(t_string);
		
		YBeanType route = f.ybean("Route");
		route.addProperty(f.yprop("route", t_string));

		YAtomicType t_memory = f.yatomic("Memory");
		t_memory.addHints("256M", "512M", "1024M");
		t_memory.parseWith(ManifestYmlValueParsers.MEMORY);

		YAtomicType t_health_check_type = f.yenum("Health Check Type", "none", "port");

		YAtomicType t_strictly_pos_integer = f.yatomic("Strictly Positive Integer");
		t_strictly_pos_integer.parseWith(ManifestYmlValueParsers.integerAtLeast(1));

		YAtomicType t_pos_integer = f.yatomic("Positive Integer");
		t_pos_integer.parseWith(ManifestYmlValueParsers.POS_INTEGER);

		YType t_env = f.ymap(t_string, t_string);

		// define schema structure...
		TOPLEVEL_TYPE.addProperty("applications", f.yseq(application));
		TOPLEVEL_TYPE.addProperty("inherit", t_string, descriptionFor("inherit"));

		YTypedPropertyImpl[] props = {
			f.yprop("buildpack", t_buildpack),
			f.yprop("command", t_string),
			f.yprop("disk_quota", t_memory),
			f.yprop("domain", t_string),
			f.yprop("domains", t_strings),
			f.yprop("env", t_env),
			f.yprop("host", t_string),
			f.yprop("hosts", t_strings),
			f.yprop("instances", t_strictly_pos_integer),
			f.yprop("memory", t_memory),
			f.yprop("name", t_string),
			f.yprop("no-hostname", t_boolean),
			f.yprop("no-route", t_boolean),
			f.yprop("path", t_path),
			f.yprop("random-route", t_boolean),
			f.yprop("routes", f.yseq(route)),
			f.yprop("services", t_strings),
			f.yprop("stack", t_string),
			f.yprop("timeout", t_pos_integer),
			f.yprop("health-check-type", t_health_check_type)
		};

		for (YTypedPropertyImpl prop : props) {
			prop.setDescriptionProvider(descriptionFor(prop));
			if (!TOPLEVEL_EXCLUDED.contains(prop.getName())) {
				TOPLEVEL_TYPE.addProperty(prop);
			}
			application.addProperty(prop);
		}
	}

	private Provider<HtmlSnippet> descriptionFor(String propName) {
		return DescriptionProviders.fromClasspath(this.getClass(), "/description-by-prop-name/"+propName+".html");
	}

	private Provider<HtmlSnippet> descriptionFor(YTypedPropertyImpl prop) {
		return descriptionFor(prop.getName());
	}

	@Override
	public YBeanType getTopLevelType() {
		return TOPLEVEL_TYPE;
	}

	@Override
	public YTypeUtil getTypeUtil() {
		return TYPE_UTIL;
	}
}
