/*
 * $Revision: 96 $
 * $Date: 2007-02-11 21:49:34 -0800 (Sun, 11 Feb 2007) $
 *
 * Copyright (c) 2007 O'Reilly Media.  All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you
 * may not use this file except in compliance with the License. You may
 * obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 * implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */

package com.oreilly.tomcat.valve;

import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.servlet.ServletException;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.catalina.connector.Request;
import org.apache.catalina.connector.Response;
import org.apache.catalina.util.ParameterMap;
import org.apache.catalina.valves.RequestFilterValve;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;


/**
 * Filters out bad user input from HTTP requests to avoid malicious
 * attacks including Cross Site Scripting (XSS), SQL Injection, and
 * HTML Injection vulnerabilities, among others.
 *
 * @author Jason Brittain
 */
public class BadInputValve extends RequestFilterValve {

    // --------------------------------------------- Static Variables

    /**
     * The Log instance to log with.
     */
    private static Log log = LogFactory.getLog(BadInputValve.class);

    /**
     * Descriptive information about this implementation.
     */
    protected static String info =
        "com.oreilly.tomcat.valve.BadInputValve/2.0";

    /**
     * An empty String array to re-use as a type indicator for toArray().
     */
    private static final String[] STRING_ARRAY = new String[0];

    // ------------------------------------------- Instance Variables

    /**
     * The flag that determines whether or not to escape quotes that are
     * part of the request.
     */
    protected boolean escapeQuotes = false;

    /**
     * The flag that determines whether or not to escape angle brackets
     * that are part of the request.
     */
    protected boolean escapeAngleBrackets = false;

    /**
     * The flag that determines whether or not to escape JavaScript
     * function and object names that are part of the request.
     */
    protected boolean escapeJavaScript = false;

    /**
     * A substitution mapping (regular expression to match, replacement)
     * that is used to replace single quotes (') and double quotes (")
     * with escaped equivalents that can't be used for malicious purposes.
     */
    protected HashMap<String, String> quotesHashMap =
        new HashMap<String, String>();

    /**
     * A substitution mapping (regular expression to match, replacement)
     * that is used to replace angle brackets (<>) with escaped
     * equivalents that can't be used for malicious purposes.
     */
    protected HashMap<String, String> angleBracketsHashMap =
        new HashMap<String, String>();

    /**
     * A substitution mapping (regular expression to match, replacement)
     * that is used to replace potentially dangerous JavaScript function
     * calls with escaped equivalents that can't be used for malicious
     * purposes.
     */
    protected HashMap<String, String> javaScriptHashMap =
        new HashMap<String, String>();
    
    /**
     * A Map of regular expressions used to filter the parameters.  The key
     * is the regular expression String to search for, and the value is the
     * regular expression String used to modify the parameter if the search
     * String is found.
     */
    protected HashMap<String, String> parameterEscapes =
        new HashMap<String, String>();

    // ------------------------------------------------- Constructors

    /**
     * Construct a new instance of this class with default property values.
     */
    public BadInputValve() {

        super();
        
        // Populate the regex escape maps.
        quotesHashMap.put("\"", "&quot;");
        quotesHashMap.put("\'", "&#39;");
        quotesHashMap.put("`", "&#96;");
        angleBracketsHashMap.put("<", "&lt;");
        angleBracketsHashMap.put(">", "&gt;");
        javaScriptHashMap.put(
            "document(.*)\\.(.*)cookie", "document&#46;&#99;ookie");
        javaScriptHashMap.put("eval(\\s*)\\(", "eval&#40;");
        javaScriptHashMap.put("setTimeout(\\s*)\\(", "setTimeout$1&#40;");
        javaScriptHashMap.put("setInterval(\\s*)\\(", "setInterval$1&#40;");
        javaScriptHashMap.put("execScript(\\s*)\\(", "exexScript$1&#40;");
        javaScriptHashMap.put("(?i)javascript(?-i):", "javascript&#58;");

        log.info("BadInputValve instantiated.");

    }

    // --------------------------------------------------- Properties

    /**
     * Gets the flag which determines whether this Valve will escape
     * any quotes (both double and single quotes) that are part of the
     * request, before the request is performed.
     */
    public boolean getEscapeQuotes() {

        return escapeQuotes;

    }

    /**
     * Sets the flag which determines whether this Valve will escape
     * any quotes (both double and single quotes) that are part of the
     * request, before the request is performed.
     *
     * @param escapeQuotes
     */
    public void setEscapeQuotes(boolean escapeQuotes) {

        this.escapeQuotes = escapeQuotes;
        if (escapeQuotes) {
            // Escape all quotes.
            parameterEscapes.putAll(quotesHashMap);
        }

    }

    /**
     * Gets the flag which determines whether this Valve will escape
     * any angle brackets that are part of the request, before the
     * request is performed.
     */
    public boolean getEscapeAngleBrackets() {

        return escapeAngleBrackets;

    }

    /**
     * Sets the flag which determines whether this Valve will escape
     * any angle brackets that are part of the request, before the
     * request is performed.
     *
     * @param escapeAngleBrackets
     */
    public void setEscapeAngleBrackets(boolean escapeAngleBrackets) {

        this.escapeAngleBrackets = escapeAngleBrackets;
        if (escapeAngleBrackets) {
            // Escape all angle brackets.
            parameterEscapes.putAll(angleBracketsHashMap);
        }

    }

    /**
     * Gets the flag which determines whether this Valve will escape
     * any potentially dangerous references to JavaScript functions
     * and objects that are part of the request, before the request is
     * performed.
     */
    public boolean getEscapeJavaScript() {

        return escapeJavaScript;

    }

    /**
     * Sets the flag which determines whether this Valve will escape
     * any potentially dangerous references to JavaScript functions
     * and objects that are part of the request, before the request is
     * performed.
     *
     * @param escapeJavaScript
     */
    public void setEscapeJavaScript(boolean escapeJavaScript) {

        this.escapeJavaScript = escapeJavaScript;
        if (escapeJavaScript) {
            // Escape potentially dangerous JavaScript method calls.
            parameterEscapes.putAll(javaScriptHashMap);
        }

    }

    /**
     * Return descriptive information about this Valve implementation.
     */
    public String getInfo() {

        return info;

    }

    // ----------------------------------------------- Public Methods

    /**
     * Sanitizes request parameters before bad user input gets into the
     * web application.
     *
     * @param request The servlet request to be processed
     * @param response The servlet response to be created
     *
     * @exception IOException if an input/output error occurs
     * @exception ServletException if a servlet error occurs
     */
    @Override
    public void invoke(Request request, Response response)
        throws IOException, ServletException {

        // Skip filtering for non-HTTP requests and responses.
        if (!(request instanceof HttpServletRequest) ||
            !(response instanceof HttpServletResponse)) {
            getNext().invoke(request, response);
            return;
        }

        // Only let requests through based on the allows and denies.
        if (processAllowsAndDenies(request, response)) {

            // Filter the input for potentially dangerous JavaScript
            // code so that bad user input is cleaned out of the request
            // by the time Tomcat begins to perform the request.
            filterParameters(request);

            // Perform the request.
            getNext().invoke(request, response);
        }

    }

    /**
     * Uses the functionality of the (abstract) RequestFilterValve to
     * stop requests that contain forbidden string patterns in parameter
     * names and parameter values.
     *
     * @param request The servlet request to be processed
     * @param response The servlet response to be created
     *
     * @exception IOException if an input/output error occurs
     * @exception ServletException if a servlet error occurs
     *
     * @return false if the request is forbidden, true otherwise.
     */
    public boolean processAllowsAndDenies(Request request, Response response)
        throws IOException, ServletException {

        ParameterMap paramMap =
            (ParameterMap) ((HttpServletRequest) request).getParameterMap();
        // Loop through the list of parameters.
        Iterator y = paramMap.keySet().iterator();
        while (y.hasNext()) {
            String name = (String) y.next();
            String[] values = ((HttpServletRequest)
                request).getParameterValues(name);

            // See if the name contains a forbidden pattern.
            if (!checkAllowsAndDenies(name, response)) {
                return false;
            }

            // Check the parameter's values for the pattern.
            if (values != null) {
                for (int i = 0; i < values.length; i++) {
                    String value = values[i];
                    if (!checkAllowsAndDenies(value, response)) {
                        return false;
                    }
                }
            }
        }

        // No parameter caused a deny.  The request should continue.
        return true;

    }

    /**
     * Perform the filtering that has been configured for this Valve,
     * matching against the specified request property. If the request
     * is allowed to proceed, this method returns true.  Otherwise,
     * this method sends a Forbidden error response page, and returns
     * false.
     *
     * <br><br>
     *
     * This method borrows heavily from RequestFilterValve.process(),
     * only this method has a boolean return type and doesn't call
     * getNext().invoke(request, response).
     *
     * @param property The request property on which to filter
     * @param response The servlet response to be processed
     *
     * @exception IOException if an input/output error occurs
     * @exception ServletException if a servlet error occurs
     *
     * @return true if the request is still allowed to proceed.
     */
    public boolean checkAllowsAndDenies(String property, Response response)
        throws IOException, ServletException {

        // If there were no denies and no allows, process the request.
        if (denies.length == 0 && allows.length == 0) {
            return true;
        }

        // Check the deny patterns, if any
        for (int i = 0; i < denies.length; i++) {
            Matcher m = denies[i].matcher(property);
            if (m.find()) {
                ServletResponse sres = response.getResponse();
                if (sres instanceof HttpServletResponse) {
                    HttpServletResponse hres = (HttpServletResponse) sres;
                    hres.sendError(HttpServletResponse.SC_FORBIDDEN);
                    return false;
                }
            }
        }

        // Check the allow patterns, if any
        for (int i = 0; i < allows.length; i++) {
            Matcher m = allows[i].matcher(property);
            if (m.find()) {
                return true;
            }
        }

        // Allow if denies specified but not allows
        if (denies.length > 0 && allows.length == 0) {
            return true;
        }

        // Otherwise, deny the request.
        ServletResponse sres = response.getResponse();
        if (sres instanceof HttpServletResponse) {
            HttpServletResponse hres = (HttpServletResponse) sres;
            hres.sendError(HttpServletResponse.SC_FORBIDDEN);
        }
        return false;

    }

    /**
     * Filters all existing parameters for potentially dangerous content,
     * and escapes any if they are found.
     *
     * @param request The Request that contains the parameters.
     */
    public void filterParameters(Request request) {

        ParameterMap paramMap =
            (ParameterMap) ((HttpServletRequest) request).getParameterMap();
        // Unlock the parameters map so we can modify the parameters.
        paramMap.setLocked(false);

        // Loop through each of the substitution patterns.
        Iterator escapesIterator = parameterEscapes.keySet().iterator();
        while (escapesIterator.hasNext()) {
            String patternString = (String) escapesIterator.next();
            Pattern pattern = Pattern.compile(patternString);

            // Loop through the list of parameters.
            @SuppressWarnings("unchecked")
            String[] paramNames =
                (String[]) paramMap.keySet().toArray(STRING_ARRAY);
            for (int i = 0; i < paramNames.length; i++) {
                String name = paramNames[i];
                String[] values = ((HttpServletRequest)
                    request).getParameterValues(name);
                // See if the name contains the pattern.
                boolean nameMatch;
                Matcher matcher = pattern.matcher(name);
                nameMatch = matcher.find();
                if (nameMatch) {
                    // The parameter's name matched a pattern, so we
                    // fix it by modifying the name, adding the parameter
                    // back as the new name, and removing the old one.
                    String newName = matcher.replaceAll(
                        (String) parameterEscapes.get(patternString));
                    request.addParameter(newName, values);
                    paramMap.remove(name);
                    log.warn("Parameter name " + name +
                        " matched pattern \"" + patternString +
                        "\".  Remote addr: " +
                        ((HttpServletRequest) request).getRemoteAddr());
                }
                // Check the parameter's values for the pattern.
                if (values != null) {
                    for (int j = 0; j < values.length; j++) {
                        String value = values[j];
                        boolean valueMatch;
                        matcher = pattern.matcher(value);
                        valueMatch = matcher.find();
                        if (valueMatch) {
                            // The value matched, so we modify the value
                            // and then set it back into the array.
                            String newValue;
                            newValue = matcher.replaceAll((String)
                                parameterEscapes.get(patternString));
                            values[j] = newValue;
                            log.warn("Parameter \"" + name +
                                "\"'s value \"" + value +
                                "\" matched pattern \"" +
                                patternString + "\".  Remote addr: " +
                                ((HttpServletRequest)
                                    request).getRemoteAddr());
                        }
                    }
                }
            }
        }
        // Make sure the parameters map is locked again when we're done.
        paramMap.setLocked(true);

    }

    /**
     * Return a text representation of this object.
     */
    @Override
    public String toString() {

        return "BadInputValve";

    }
}
