/*
 * $Revision: 99 $
 * $Date: 2007-02-13 22:26:42 -0800 (Tue, 13 Feb 2007) $
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

package com.oreilly.tomcat.filter;

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;


/**
 * Filters out bad user input from HTTP requests to avoid malicious
 * attacks including Cross Site Scripting (XSS), SQL Injection, and
 * HTML Injection vulnerabilities, among others.
 *
 * @author Jason Brittain
 */
public class BadInputFilter implements Filter {

    // --------------------------------------------- Static Variables

    /**
     * Descriptive information about this implementation.
     */
    protected static String info =
        "com.oreilly.tomcat.filter.BadInputFilter/2.0";

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
     * The comma-delimited set of <code>allow</code> expressions.
     */
    protected String allow = null;

    /**
     * The set of <code>allow</code> regular expressions we will evaluate.
     */
    protected Pattern allows[] = new Pattern[0];

    /**
     * The set of <code>deny</code> regular expressions we will evaluate.
     */
    protected Pattern denies[] = new Pattern[0];

    /**
     * The comma-delimited set of <code>deny</code> expressions.
     */
    protected String deny = null;

    /**
     * A Map of regular expressions used to filter the parameters.  The key
     * is the regular expression String to search for, and the value is the
     * regular expression String used to modify the parameter if the search
     * String is found.
     */
    protected HashMap<String, String> parameterEscapes =
        new HashMap<String, String>();

    /**
     * The ServletContext under which this Filter runs.  Used for logging.
     */
    protected ServletContext servletContext;

    /**
     * On Tomcat, the parameterMap must be unlocked, modified, then
     * unlocked.  But, the class that has the method to do that is part
     * of Tomcat, not part of the servlet API, so that class shouldn't
     * be visible to webapps, although it is, by default, on Tomcat 6.0.
     * This Filter uses reflection to invoke it, if it's there.
     */
    protected Method setLockedMethod;

    // ------------------------------------------------- Constructors

    /**
     * Construct a new instance of this class with default property values.
     */
    public BadInputFilter() {

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

    }

    // --------------------------------------------------- Properties

    /**
     * Gets the flag which determines whether this Filter will escape
     * any quotes (both double and single quotes) that are part of the
     * request, before the request is performed.
     */
    public boolean getEscapeQuotes() {

        return escapeQuotes;

    }

    /**
     * Sets the flag which determines whether this Filter will escape
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
     * Gets the flag which determines whether this Filter will escape
     * any angle brackets that are part of the request, before the
     * request is performed.
     */
    public boolean getEscapeAngleBrackets() {

        return escapeAngleBrackets;

    }

    /**
     * Sets the flag which determines whether this Filter will escape
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
     * Gets the flag which determines whether this Filter will escape
     * any potentially dangerous references to JavaScript functions
     * and objects that are part of the request, before the request is
     * performed.
     */
    public boolean getEscapeJavaScript() {

        return escapeJavaScript;

    }

    /**
     * Sets the flag which determines whether this Filter will escape
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
     * Return a comma-delimited set of the <code>allow</code> expressions
     * configured for this Filter, if any; otherwise, return <code>null</code>.
     */
    public String getAllow() {

        return (this.allow);

    }

    /**
     * Set the comma-delimited set of the <code>allow</code> expressions
     * configured for this Filter, if any.
     *
     * @param allow The new set of allow expressions
     */
    public void setAllow(String allow) {

        this.allow = allow;
        allows = precalculate(allow);
        servletContext.log("BadInputFilter: allow = " + deny);

    }

    /**
     * Return a comma-delimited set of the <code>deny</code> expressions
     * configured for this Filter, if any; otherwise, return
     * <code>null</code>.
     */
    public String getDeny() {

        return (this.deny);

    }

    /**
     * Set the comma-delimited set of the <code>deny</code> expressions
     * configured for this Filter, if any.
     *
     * @param deny The new set of deny expressions
     */
    public void setDeny(String deny) {

        this.deny = deny;
        denies = precalculate(deny);
        servletContext.log("BadInputFilter: deny = " + deny);

    }

    // ----------------------------------------------- Public Methods

    /**
     * {@inheritDoc}
     */
    public void init(FilterConfig filterConfig) throws ServletException {

        servletContext = filterConfig.getServletContext();
        
        // Parse the Filter's init parameters.
        setAllow(filterConfig.getInitParameter("allow"));
        setDeny(filterConfig.getInitParameter("deny"));
        String initParam = filterConfig.getInitParameter("escapeQuotes");
        if (initParam != null) {
            boolean flag = Boolean.parseBoolean(initParam);
            setEscapeQuotes(flag);
        }
        initParam = filterConfig.getInitParameter("escapeAngleBrackets");
        if (initParam != null) {
            boolean flag = Boolean.parseBoolean(initParam);
            setEscapeAngleBrackets(flag);
        }
        initParam = filterConfig.getInitParameter("escapeJavaScript");
        if (initParam != null) {
            boolean flag = Boolean.parseBoolean(initParam);
            setEscapeJavaScript(flag);
        }

        servletContext.log(toString() + " initialized.");

    }
    
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
    public void doFilter(ServletRequest request, ServletResponse response,
                             FilterChain filterChain)
        throws IOException, ServletException {

        // Skip filtering for non-HTTP requests and responses.
        if (!(request instanceof HttpServletRequest) ||
            !(response instanceof HttpServletResponse)) {
            filterChain.doFilter(request, response);
            return;
        }

        // Only let requests through based on the allows and denies.
        if (processAllowsAndDenies(request, response)) {

            // Filter the input for potentially dangerous JavaScript
            // code so that bad user input is cleaned out of the request
            // by the time Tomcat begins to perform the request.
            filterParameters(request);

            // Perform the request.
            filterChain.doFilter(request, response);
        }
        
    }

    /**
     * Stops requests that contain forbidden string patterns in parameter
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
    public boolean processAllowsAndDenies(ServletRequest request,
                                          ServletResponse response)
        throws IOException, ServletException {

        Map paramMap = request.getParameterMap();
        // Loop through the list of parameters.
        Iterator y = paramMap.keySet().iterator();
        while (y.hasNext()) {
            String name = (String) y.next();
            String[] values = request.getParameterValues(name);

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
     * Perform the filtering that has been configured for this Filter,
     * matching against the specified request property. If the request
     * is allowed to proceed, this method returns true.  Otherwise,
     * this method sends a Forbidden error response page, and returns
     * false.
     *
     * <br><br>
     *
     * This method borrows heavily from RequestFilterValve.process().
     *
     * @param property The request property on which to filter
     * @param response The servlet response to be processed
     *
     * @exception IOException if an input/output error occurs
     * @exception ServletException if a servlet error occurs
     *
     * @return true if the request is still allowed to proceed.
     */
    public boolean checkAllowsAndDenies(String property,
                                        ServletResponse response)
        throws IOException, ServletException {

        // If there were no denies and no allows, process the request.
        if (denies.length == 0 && allows.length == 0) {
            return true;
        }
        
        // Check the deny patterns, if any
        for (int i = 0; i < denies.length; i++) {
            Matcher m = denies[i].matcher(property);
            if (m.find()) {
                if (response instanceof HttpServletResponse) {
                    HttpServletResponse hres =
                        (HttpServletResponse) response;
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
        if (response instanceof HttpServletResponse) {
            HttpServletResponse hres = (HttpServletResponse) response;
            hres.sendError(HttpServletResponse.SC_FORBIDDEN);
        }
        return false;
        
    }

    /**
     * Filters all existing parameters for potentially dangerous content,
     * and escapes any if they are found.
     *
     * @param request The ServletRequest that contains the parameters.
     */
    @SuppressWarnings("unchecked")
    public void filterParameters(ServletRequest request) {

        Map paramMap = ((HttpServletRequest) request).getParameterMap();
        // Try to unlock the parameters map so we can modify the parameters.
        try {
            if (setLockedMethod == null) {
                setLockedMethod = paramMap.getClass().getMethod(
                    "setLocked", new Class[] { Boolean.TYPE });
            }
            setLockedMethod.invoke(paramMap, new Object[] { Boolean.FALSE });
        } catch (Exception e) {
            // Unable to unlock the parameters, and if this occurs while
            // running on Tomcat, we cannot filter the parameters.
            servletContext.log("BadInputFilter: Cannot filter parameters!");
        }
        
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
                nameMatch = matcher.matches();
                if (nameMatch) {
                    // The parameter's name matched a pattern, so we
                    // fix it by modifying the name, adding the parameter
                    // back as the new name, and removing the old one.
                    String newName = matcher.replaceAll(
                        (String) parameterEscapes.get(patternString));
                    paramMap.remove(name);
                    paramMap.put(newName, values);
                    servletContext.log("Parameter name " + name +
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
                            servletContext.log("Parameter \"" + name +
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

        // Try to lock the parameters map again when we're done.
        try {
            if (setLockedMethod == null) {
                setLockedMethod = paramMap.getClass().getMethod(
                    "setLocked", new Class[] { Boolean.TYPE });
            }
            setLockedMethod.invoke(paramMap, new Object[] { Boolean.TRUE });
        } catch (Exception e) {
            // We already logged about this, so do nothing here.
        }
        
    }

    /**
     * Return a text representation of this object.
     */
    @Override
    public String toString() {

        return "BadInputFilter";

    }

    /**
     * {@inheritDoc}
     */
    public void destroy() {
                
    }
        
    // -------------------------------------------- Protected Methods

    /**
     * Return an array of regular expression objects initialized from the
     * specified argument, which must be <code>null</code> or a
     * comma-delimited list of regular expression patterns.
     *
     * @param list The comma-separated list of patterns
     *
     * @exception IllegalArgumentException if one of the patterns has
     *  invalid syntax
     */
    protected Pattern[] precalculate(String list) {

        if (list == null)
            return (new Pattern[0]);
        list = list.trim();
        if (list.length() < 1)
            return (new Pattern[0]);
        list += ",";

        ArrayList<Pattern> reList = new ArrayList<Pattern>();
        while (list.length() > 0) {
            int comma = list.indexOf(',');
            if (comma < 0)
                break;
            String pattern = list.substring(0, comma).trim();
            try {
                reList.add(Pattern.compile(pattern));
            } catch (PatternSyntaxException e) {
                IllegalArgumentException iae = new IllegalArgumentException(
                    "Syntax error in request filter pattern" + pattern);
                iae.initCause(e);
                throw iae;
            }
            list = list.substring(comma + 1);
        }

        Pattern reArray[] = new Pattern[reList.size()];
        return ((Pattern[]) reList.toArray(reArray));

    }
}
