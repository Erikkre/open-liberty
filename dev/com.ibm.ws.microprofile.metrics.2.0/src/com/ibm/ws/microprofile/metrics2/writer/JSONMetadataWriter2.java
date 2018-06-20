/*******************************************************************************
 * Copyright (c) 2017 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package com.ibm.ws.microprofile.metrics2.writer;

import java.io.Writer;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;

import org.eclipse.microprofile.metrics.Metadata;

import com.ibm.json.java.JSONObject;
import com.ibm.websphere.ras.Tr;
import com.ibm.websphere.ras.TraceComponent;
import com.ibm.ws.microprofile.metrics.writer.JSONMetadataWriter;
import com.ibm.ws.microprofile.metrics.writer.OutputWriter;

/**
 *
 */
public class JSONMetadataWriter2 extends JSONMetadataWriter implements OutputWriter {

    private static final TraceComponent tc = Tr.register(JSONMetadataWriter.class);

    public JSONMetadataWriter2(Writer writer, Locale locale) {
        super(writer, locale);
    }

    @Override
    protected JSONObject getJsonFromMetricMetadataMap(Map<String, Metadata> metadataMap) {
        JSONObject jsonObject = new JSONObject();
        for (Entry<String, Metadata> entry : metadataMap.entrySet()) {
            jsonObject.put(entry.getKey(), getJsonFromObject(entry.getValue()));
        }
        return jsonObject;
    }

    @Override
    protected JSONObject getJsonFromObject(Metadata metadata) {
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("name", sanitizeMetadata(metadata.getName()));
        jsonObject.put("displayName", sanitizeMetadata(metadata.getDisplayName()));
        //Check TR.formatMessage for performance impact
        jsonObject.put("description", Tr.formatMessage(tc, locale, sanitizeMetadata(metadata.getDescription().get())));
        jsonObject.put("type", sanitizeMetadata(metadata.getType()));
        jsonObject.put("unit", sanitizeMetadata(metadata.getUnit().get()));
        jsonObject.put("tags", getJsonFromMap(metadata.getTags()));

        return jsonObject;
    }

}
