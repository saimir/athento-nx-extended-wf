package org.athento.nuxeo.wf.utils;

import org.apache.commons.lang.StringEscapeUtils;
import org.nuxeo.ecm.automation.core.scripting.DateWrapper;

import java.io.Serializable;
import java.util.Calendar;
import java.util.Date;

/**
 * Created by victorsanchez on 25/11/16.
 */
public class Functions implements Serializable {

    public DateWrapper date(Date date) {
        return new DateWrapper(date);
    }

    public DateWrapper calendar(Calendar date) {
        return new DateWrapper(date);
    }

    public String escapeHtml(Object obj) {
        return StringEscapeUtils.escapeHtml(obj.toString());
    }

    public String removeUserPrefix(String username) {
        if (username.startsWith("user:")) {
            username = username.replace("user:", "");
        } else if (username.startsWith("group:")) {
            username = username.replace("group:", "");
        }
        return username;
    }

    public boolean startsWith(String str, String prefix) {
        return str.startsWith(prefix);
    }
}
