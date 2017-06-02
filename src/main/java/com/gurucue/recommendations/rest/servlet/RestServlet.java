/*
 * This file is part of Guru Cue Search & Recommendation Engine.
 * Copyright (C) 2017 Guru Cue Ltd.
 *
 * Guru Cue Search & Recommendation Engine is free software: you can
 * redistribute it and/or modify it under the terms of the GNU General
 * Public License as published by the Free Software Foundation, either
 * version 3 of the License, or (at your option) any later version.
 *
 * Guru Cue Search & Recommendation Engine is distributed in the hope
 * that it will be useful, but WITHOUT ANY WARRANTY; without even the
 * implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Guru Cue Search & Recommendation Engine. If not, see
 * <http://www.gnu.org/licenses/>.
 */
package com.gurucue.recommendations.rest.servlet;

import com.gurucue.recommendations.ProcessingException;
import com.gurucue.recommendations.ResponseException;
import com.gurucue.recommendations.ResponseStatus;
import com.gurucue.recommendations.Transaction;
import com.gurucue.recommendations.data.DataManager;
import com.gurucue.recommendations.rest.data.*;
import com.gurucue.recommendations.rest.data.response.RestResponse;
import com.gurucue.recommendations.DatabaseException;
import com.gurucue.recommendations.translator.DataTranslator;
import com.gurucue.recommendations.translator.TranslatorAware;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;

/**
 * <p>
 *     Base servlet class that implements support methods to handle
 *     REST service requests. All of the REST API must inherit from
 *     this class.
 * </p>
 * <p>
 *     A child class must override one or more of the methods:
 *     <ul>
 *         <li>{@link #restGet(com.gurucue.recommendations.rest.data.RequestCache, String[])} to service <code>GET</code> requests</li>
 *         <li>{@link #restPost(com.gurucue.recommendations.rest.data.RequestCache, String[], MimeType, String)} to service <code>POST</code> requests</li>
 *         <li>{@link #restPut(com.gurucue.recommendations.rest.data.RequestCache, String[], MimeType, String)} to service <code>PUT</code> requests</li>
 *         <li>{@link #restDelete(com.gurucue.recommendations.rest.data.RequestCache, String[])} to service <code>DELETE</code> requests</li>
 *     </ul>
 *     If a method is not overridden, then upon servicing the corresponding
 *     request a 405 (Method Not Allowed) error is returned with the list
 *     of allowed methods in the <code>Allowed</code> header.
 * </p>
 * <p>
 *     The base class methods process all the meta-data concerning
 *     the requested format of the response (XML or JSON), as well as the
 *     entity format in the request for the POST and PUT requests and any
 *     additional path in the URI that follows the path part of the
 *     servlet for GET and DELETE requests. The processing is then delegated
 *     to one of the overridden methods (<code>restGet</code>,
 *     <code>restPost</code>, <code>restPut</code>, or <code>restDelete</code>)
 *     which must either return a {@link RestResponse} or one of its
 *     sub-classes, or throw a {@link ResponseException}. This is then
 *     auto-converted into the requested response format and returned to the
 *     client.
 * </p>
 * </p>
 */
public abstract class RestServlet extends HttpServlet {
    private static final String HEADER_CERTIFICATE_SUBJECT_CN = "X-Cert-Subject-CN";
    private static final int RETRY_COUNT_ON_DB_ERROR = 1;
    private static final long RETRY_DELAY_ON_DB_ERROR = 100L;

    protected final String allowedHttpMethods;
    protected final String servletName;

    protected final GetInvoker getInvoker;
    protected final PostInvoker postInvoker;
    protected final PutInvoker putInvoker;
    protected final DeleteInvoker deleteInvoker;

    /**
     * Checks what <code>rest*</code> methods are overridden, so it can
     * fail-fast on HTTP methods that won't get processed, and constructs
     * the <code>Allowed</code> header value for such cases.
     */
    public RestServlet(final String servletName) {
        this.servletName = servletName;
        final Class<? extends HttpServlet> c = getClass();
        final StringBuilder sb = new StringBuilder();

        if (isMethodOverridden(c, "restGet", new Class[] {RequestCache.class, String[].class})) {
            sb.append("GET");
            getInvoker = new GetInvoker(this);
        }
        else getInvoker = null;

        if (isMethodOverridden(c, "restPost" , new Class[] {RequestCache.class, String[].class, MimeType.class, String.class})) {
            if (getInvoker != null) sb.append(", ");
            sb.append("POST");
            postInvoker = new PostInvoker(this);
        }
        else postInvoker = null;

        if (isMethodOverridden(c, "restPut", new Class[] {RequestCache.class, String[].class, MimeType.class, String.class})) {
            if ((getInvoker != null) || (postInvoker != null)) sb.append(", ");
            sb.append("PUT");
            putInvoker = new PutInvoker(this);
        }
        else putInvoker = null;

        if (isMethodOverridden(c, "restDelete", new Class[] {RequestCache.class, String[].class})) {
            if ((getInvoker != null) || (postInvoker != null) || (putInvoker != null)) sb.append(", ");
            sb.append("DELETE");
            deleteInvoker = new DeleteInvoker(this);
        }
        else deleteInvoker = null;

        allowedHttpMethods = sb.toString();
    }

    /**
     * Handles the HTTP GET method for REST services by pre-processing the
     * HTTP headers and any additional path in the URI, invoking the
     * {@link #restGet(com.gurucue.recommendations.rest.data.RequestCache, String[])} )} method to obtain the response object, and
     * transforming the response object into the client HTTP response.
     * The following restrictions are implemented:
     * <ul>
     *     <li>if the GET method is not enabled (i.e. not overridden in the
     *     subclass), then a 405 Method Not Allowed error is returned to the
     *     client,</li>
     *     <li>if the response format is not supported or not found, then a
     *     406 Not Acceptable error is returned to the client,</li>
     *     <li>if there is a syntax error in <code>Accept</code> headers, then
     *     a 400 Bad Request error is returned to the client,</li>
     *     <li>if the request contains too many path-info fragments, then a
     *     414 Request-URI Too Long is returned to the client.</li>
     * </ul>
     * If the response processing method {@link #restGet(com.gurucue.recommendations.rest.data.RequestCache, String[])} encounters
     * a fatal error, then it should throw a {@link ResponseException}, which
     * is then caught by this method and transformed into the client HTTP
     * response.
     *
     * @param req
     * @param resp
     * @throws ServletException
     * @throws IOException
     */
    @Override
    final protected void doGet(final HttpServletRequest req, final HttpServletResponse resp) throws ServletException, IOException {
        process(req, resp, false, getInvoker);
    }

    /**
     * Handles the HTTP POST method for REST services by pre-processing the
     * HTTP headers, reading the input into a String, making sure there is
     * no additional path path in the URI after the REST service path,
     * invoking the {@link #restPost(com.gurucue.recommendations.rest.data.RequestCache, String[], MimeType, String)} method to obtain the
     * response object, and transforming the response object into the client
     * HTTP response.
     * The following restrictions are implemented:
     * <ul>
     *     <li>if the POST method is not enabled (i.e. not overridden in the
     *     subclass), then a 405 Method Not Allowed error is returned to the
     *     client,</li>
     *     <li>if the request contains any path-info fragments, then a
     *     414 Request-URI Too Long is returned to the client,</li>
     *     <li>if the request format is not supported, then a 415 Unsupported
     *     Media Type is returned to the client</li>
     *     <li>if the response format is not supported or not found, then a
     *     406 Not Acceptable error is returned to the client,</li>
     *     <li>if there is a syntax error in <code>Accept</code> headers, then
     *     a 400 Bad Request error is returned to the client,</li>
     *     <li>if the request entity is too big, then a 413 Request Entity Too
     *     Large is returned to the client.</li>
     * </ul>
     * If the response processing method {@link #restPost(com.gurucue.recommendations.rest.data.RequestCache, String[], MimeType, String)}
     * encounters a fatal error, then it should throw a {@link ResponseException},
     * which is then caught by this method and transformed into the client
     * HTTP response.
     *
     * @param req
     * @param resp
     * @throws ServletException
     * @throws IOException
     */
    @Override
    final protected void doPost(final HttpServletRequest req, final HttpServletResponse resp) throws ServletException, IOException {
        process(req, resp, true, postInvoker);
    }

    /**
     * Handles the HTTP PUT method for REST services by pre-processing the
     * HTTP headers, reading the input into a String, making sure there is
     * no additional path path in the URI after the REST service path,
     * invoking the {@link #restPut(com.gurucue.recommendations.rest.data.RequestCache, String[], MimeType, String)} method to obtain the
     * response object, and transforming the response object into the client
     * HTTP response.
     * The following restrictions are implemented:
     * <ul>
     *     <li>if the PUT method is not enabled (i.e. not overridden in the
     *     subclass), then a 405 Method Not Allowed error is returned to the
     *     client,</li>
     *     <li>if the request contains any path-info fragments, then a
     *     414 Request-URI Too Long is returned to the client,</li>
     *     <li>if the request format is not supported, then a 415 Unsupported
     *     Media Type is returned to the client</li>
     *     <li>if the response format is not supported or not found, then a
     *     406 Not Acceptable error is returned to the client,</li>
     *     <li>if there is a syntax error in <code>Accept</code> headers, then
     *     a 400 Bad Request error is returned to the client,</li>
     *     <li>if the request entity is too big, then a 413 Request Entity Too
     *     Large is returned to the client.</li>
     * </ul>
     * If the response processing method {@link #restPut(com.gurucue.recommendations.rest.data.RequestCache, String[], MimeType, String)}
     * encounters a fatal error, then it should throw a {@link ResponseException},
     * which is then caught by this method and transformed into the client
     * HTTP response.
     *
     * @param req
     * @param resp
     * @throws ServletException
     * @throws IOException
     */
    @Override
    final protected void doPut(final HttpServletRequest req, final HttpServletResponse resp) throws ServletException, IOException {
        process(req, resp, true, putInvoker);
    }

    /**
     * Handles the HTTP DELETE method for REST services by pre-processing the
     * HTTP headers and any additional path in the URI, invoking the
     * {@link #restDelete(com.gurucue.recommendations.rest.data.RequestCache, String[])} method to obtain the response object, and
     * transforming the response object into the client HTTP response.
     * The following restrictions are implemented:
     * <ul>
     *     <li>if the DELETE method is not enabled (i.e. not overridden in the
     *     subclass), then a 405 Method Not Allowed error is returned to the
     *     client,</li>
     *     <li>if the response format is not supported or not found, then a
     *     406 Not Acceptable error is returned to the client,</li>
     *     <li>if there is a syntax error in <code>Accept</code> headers, then
     *     a 400 Bad Request error is returned to the client,</li>
     *     <li>if the request contains too many path-info fragments, then a
     *     414 Request-URI Too Long is returned to the client.</li>
     * </ul>
     * If the response processing method {@link #restDelete(com.gurucue.recommendations.rest.data.RequestCache, String[])} encounters
     * a fatal error, then it should throw a {@link com.gurucue.recommendations.ResponseException}, which
     * is then caught by this method and transformed into the client HTTP
     * response.
     *
     * @param req
     * @param resp
     * @throws ServletException
     * @throws IOException
     */
    @Override
    final protected void doDelete(final HttpServletRequest req, final HttpServletResponse resp) throws ServletException, IOException {
        process(req, resp, false, deleteInvoker);
    }

    /**
     * Processes the <code>Content-Type</code> header to see in what format
     * is the request entity. If the format is not supported, then the HTTP
     * error 415 Unsupported Media Type is thrown.
     * Otherwise the appropriate {@link MimeType} value is returned.
     *
     * @param contentType    the <code>Content-Type</code> header, as returned by {@link javax.servlet.http.HttpServletRequest#getContentType()}
     * @return the {@link MimeType} instance representing the request entity format
     * @throws HttpUnsupportedMediaTypeException if the Content-Type of the request is not support
     */
    private static MimeType processRequestFormat(final String contentType) throws HttpUnsupportedMediaTypeException {
        final String mimeTypeName = contentType.split(";")[0].trim();
        final MimeType requestFormat = MimeType.fromMimeTypeName(mimeTypeName);
        if (null == requestFormat) throw new HttpUnsupportedMediaTypeException("The request content type " + mimeTypeName + " is not supported");
        return requestFormat;
    }

    /**
     * Checks for path info, this is the path part of URI that follows the
     * servlet's path, and returns if there is none,
     * otherwise throws a {@link HttpRequestUriTooLongException}.
     *
     * @param pathInfo    the path info, as returned by {@link javax.servlet.http.HttpServletRequest#getPathInfo()}
     * @throws HttpRequestUriTooLongException
     */
    private static void noPathFragments(final String pathInfo) throws HttpRequestUriTooLongException {
        if ((null != pathInfo) && (pathInfo.length() > 0)) throw new HttpRequestUriTooLongException("The request URI contains superfluous sub-path: " + pathInfo);
    }

    /**
     * Returns <code>true</code> if the specified method in the specified
     * class is not implemented by the <code>RestServlet</code> class,
     * otherwise it returns <code>false</code>. This method is used in the
     * <code>RestServlet</code> constructor to see what <code>rest*</code>
     * methods are overridden, so it can optimize the fail-fast error in
     * case of unsupported HTTP methods for the REST service servlet
     * represented by the given class.
     *
     * @param c             the class extending <code>RestServlet</code> for which to check whether the given method was overridden
     * @param methodName    the name of the method for which to check whether it was overridden
     * @return <code>true</code> in case the method was overridden, <code>false</code> otherwise
     */
    private static boolean isMethodOverridden(Class<?> c, final String methodName, final Class[] methodParams) {
        while (c != RestServlet.class) { // while the class we inspect is a child class of RestServlet...
            try {
                c.getDeclaredMethod(methodName, methodParams); // try to obtain the method, declared directly in the class
                return true; // method obtained, so it exists, so it is overridden
            } catch (NoSuchMethodException e) {
                // pass
            }
            c = c.getSuperclass(); // go one class up and retry
        }
        return false; // got up to RestServlet and didn't find the method declared in any of the child classes, so it is not overridden
    }

    /**
     * Takes a {@link com.gurucue.recommendations.translator.TranslatorAware} instance that is the result of request
     * processing and constructs a response for the client in the proper
     * format and sends it.
     *
     * @param responseFormat    the format in which to send the REST service response
     * @param resp              the response object to which to write the REST service response
     * @param restResponse      the {@link RestResponse} instance that is the result of processing of the request
     */
    private static void sendResponse(final MimeType responseFormat, final HttpServletResponse resp, final TranslatorAware restResponse, final RequestCache cache) throws IOException {
        resp.setContentType(responseFormat.TYPE_NAME + ";charset=UTF-8");
        final PrintWriter writer = resp.getWriter();
        final String responseString = DataTranslator.forFormat(responseFormat.CONTENT_FORMAT.NAME).translate(restResponse);
        writer.append(responseString);
        writer.flush();
        writer.close();
        cache.getLogger().subLogger(RestServlet.class.getSimpleName()).info("Sent response:\n" + responseString);
    }

    /**
     * The method implementing processing of the HTTP GET method of a REST
     * service. Override only if your REST service supports the
     * HTTP GET operation/semantics. The method must construct a
     * {@link TranslatorAware} instance and return it
     * as a result of processing. The result will be converted
     * into the appropriate format for the client and sent. If a
     * {@link ResponseException} is thrown, then the exception is converted
     * into a response and sent to the client instead.
     *
     * @param cache            the private request data cache
     * @param pathFragments    the optional additional path fragments, that were specified in the URI after the servlet path
     * @return the {@link TranslatorAware} instance representing the result of processing
     * @throws ResponseException if a fatal error occured during processing; this error is then converted into a client response
     */
    protected TranslatorAware restGet(final RequestCache cache, final String[] pathFragments) throws ResponseException {
        throw new IllegalStateException("Invoked the unimplemented method GET on servlet " + getClass().getSimpleName());
    }

    /**
     * The method implementing processing of the HTTP POST method of a REST
     * service. Override only if your REST service supports the
     * HTTP POST operation/semantics. The method is given the request and the
     * format that the request is in, and must construct a
     * {@link RestResponse} instance or one of its subclasses and return it
     * as a result of processing. The result will be converted
     * into the appropriate format for the client and sent. If a
     * {@link ResponseException} is thrown, then the exception is converted
     * into a response and sent to the client instead.
     *
     * @param cache            the private request data cache
     * @param requestFormat    the format that the request is in
     * @param request          the request content
     * @return the {@link RestResponse} instance representing the result of processing
     * @throws ResponseException if a fatal error occured during processing; this error is then converted into a client response
     */
    protected RestResponse restPost(final RequestCache cache, final String[] pathFragments, final MimeType requestFormat, final String request) throws ResponseException {
        throw new IllegalStateException("Invoked the unimplemented method POST on servlet " + getClass().getSimpleName());
    }

    /**
     * The method implementing processing of the HTTP PUT method of a REST
     * service. Override only if your REST service supports the
     * HTTP PUT operation/semantics. The method is given the request and the
     * format that the request is in, and must construct a
     * {@link RestResponse} instance or one of its subclasses and return it
     * as a result of processing. The result will be converted
     * into the appropriate format for the client and sent. If a
     * {@link ResponseException} is thrown, then the exception is converted
     * into a response and sent to the client instead.
     *
     * @param cache            the private request data cache
     * @param requestFormat    the format that the request is in
     * @param request          the request content
     * @return the {@link RestResponse} instance representing the result of processing
     * @throws com.gurucue.recommendations.ResponseException if a fatal error occured during processing; this error is then converted into a client response
     */
    protected RestResponse restPut(final RequestCache cache, final String[] pathFragments, final MimeType requestFormat, final String request) throws ResponseException {
        throw new IllegalStateException("Invoked the unimplemented method PUT on servlet " + getClass().getSimpleName());
    }

    /**
     * The method implementing processing of the HTTP DELETE method of a REST
     * service. Override only if your REST service supports the
     * HTTP DELETE operation/semantics. The method must construct a
     * {@link RestResponse} instance or one of its subclasses and return it
     * as a result of processing. The result will be converted
     * into the appropriate format for the client and sent. If a
     * {@link ResponseException} is thrown, then the exception is converted
     * into a response and sent to the client instead.
     *
     * @param cache            the private request data cache
     * @param pathFragments    the optional additional path fragments, that were specified in the URI after the servlet path
     * @return the {@link RestResponse} instance representing the result of processing
     * @throws ResponseException if a fatal error occured during processing; this error is then converted into a client response
     */
    protected RestResponse restDelete(final RequestCache cache, final String[] pathFragments) throws ResponseException {
        throw new IllegalStateException("Invoked the unimplemented method DELETE on servlet " + getClass().getSimpleName());
    }

    final private void process(
            final HttpServletRequest req,
            final HttpServletResponse resp,
            final boolean hasBody,
            final RestProcessingInvoker invoker
    ) throws IOException {
        final long startTime = System.nanoTime();
        final String uri = req.getRequestURI();
        final String pathInfo = req.getPathInfo();
        final MimeType responseFormat;
        final String contentType;
        final String requestBody;

        TranslatorAware response;
        boolean doCommit = false;
        Throwable exception = null;
        Transaction transaction = null;
        final RequestCache cache = RequestCache.getCache(servletName);
        try {
            final RequestLogger logger = cache.getLogger().subLogger(getClass().getSimpleName());
            try {
                // process input
                final MimeType requestFormat;
                final String encodingComment;
                if (hasBody) {
                    final String characterEncoding = req.getCharacterEncoding();
                    if ((null == characterEncoding) || (characterEncoding.length() == 0)) {
                        encodingComment = " [no character encoding, overriding with UTF-8]";
                        req.setCharacterEncoding("UTF-8");
                    }
                    else encodingComment = "";
                    contentType = req.getContentType();
                    requestBody = ServletUtils.streamToString(req.getReader());
                    requestFormat = processRequestFormat(contentType);
                }
                else {
                    contentType = null;
                    requestBody = null;
                    requestFormat = null;
                    encodingComment = "";
                }
                responseFormat = ServletUtils.chooseResponseFormat(req.getParameter("format"), req.getHeaders("Accept"), requestFormat);
                final String[] pathFragments = ServletUtils.pathInfoFragments(pathInfo);

                // log the request
                final StringBuilder sb = new StringBuilder();
                sb.append(req.getMethod());
                sb.append(" ");
                sb.append(uri);
                sb.append(" [BEGIN PROCESSING]");
                if ((null != requestBody) && (requestBody.length() > 0)) {
                    sb.append(", Content-Type: ");
                    sb.append(contentType);
                    sb.append(encodingComment);
                    sb.append(", request body below:\n");
                    sb.append(requestBody);
                }
                logger.info(sb.toString());

                // see if we are able to process it
                if (invoker == null) throw new HttpMethodNotAllowedException(allowedHttpMethods);

                // process the request
                try {
                    cache.setPartner(req.getHeader(HEADER_CERTIFICATE_SUBJECT_CN));
                    int i = 0;
                    for (;;) {
                        transaction = Transaction.newTransaction(DataManager.getCurrentLink());
                        try {
                            response = invoker.process(cache, pathFragments, requestFormat, requestBody);
                            doCommit = true;
                            break;
                        }
                        catch (DatabaseException de) {
                            if (i >= RETRY_COUNT_ON_DB_ERROR) throw de;
                            i++;
                            logger.warn("Database exception (try #" + i + " out of " + RETRY_COUNT_ON_DB_ERROR + ") while servicing request, retry in " + RETRY_DELAY_ON_DB_ERROR + " ms: " + de.toString(), de);
                            transaction.rollback();
                            DataManager.removeCurrentLink(); // re-establish database connection
                            try {
                                Thread.sleep(RETRY_DELAY_ON_DB_ERROR);
                            } catch (InterruptedException e) {
                                logger.error("Interrupted while sleeping after failed try #" + i + " of servicing a request");
                            }
                        }
                    }
                }
                catch (ResponseException re) {
                    logger.error("Response exception: " + re.toString(), re);
                    response = re;
                }
                catch (ProcessingException pe) {
                    logger.error("Processing exception: " + pe.toString(), pe);
                    response = new ProcessingExceptionTranslator(pe);
                }

                final String responseString = DataTranslator.forFormat(responseFormat.CONTENT_FORMAT.NAME).translate(response);
                resp.setContentType(responseFormat.TYPE_NAME + ";charset=UTF-8");
                final PrintWriter writer = resp.getWriter();
                writer.append(responseString);
                writer.flush();
                writer.close();
                // log the timing and response status
                logger.debug("OK: " + (System.nanoTime() - startTime) + " ns, sent response:\n" + responseString);
            }
            catch (HttpException he) {
                exception = he;
                he.sendError(resp);
            }
            catch (RuntimeException | Error re) {
                exception = re;
                resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, re.toString());
            }

            // log the timing and error status
            if (exception != null) logger.debug("ERR: " + (System.nanoTime() - startTime) + " ns, " + exception.toString(), exception);
        }
        finally {
            // clean up: close the request object (cache) and the database connection
            try {
                cache.close(doCommit);
            }
            finally {
                try {
                    if (transaction != null) {
                        if (doCommit) transaction.commit();
                        else transaction.rollback();
                    }
                }
                finally {
                    DataManager.removeCurrentLink();
                }
            }
        }
    }

    interface RestProcessingInvoker {
        TranslatorAware process(RequestCache cache, String[] pathFragments, MimeType requestFormat, String request) throws ResponseException;
    }

    static class GetInvoker implements RestProcessingInvoker {
        private final RestServlet servlet;

        GetInvoker(final RestServlet servlet) {
            this.servlet = servlet;
        }

        @Override
        public TranslatorAware process(final RequestCache cache, final String[] pathFragments, final MimeType requestFormat, final String request) throws ResponseException {
            return servlet.restGet(cache, pathFragments);
        }
    }

    static class PostInvoker implements RestProcessingInvoker {
        private final RestServlet servlet;

        PostInvoker(final RestServlet servlet) {
            this.servlet = servlet;
        }

        @Override
        public RestResponse process(final RequestCache cache, final String[] pathFragments, final MimeType requestFormat, final String request) throws ResponseException {
            return servlet.restPost(cache, pathFragments, requestFormat, request);
        }
    }

    static class PutInvoker implements RestProcessingInvoker {
        private final RestServlet servlet;

        PutInvoker(final RestServlet servlet) {
            this.servlet = servlet;
        }

        @Override
        public RestResponse process(final RequestCache cache, final String[] pathFragments, final MimeType requestFormat, final String request) throws ResponseException {
            return servlet.restPut(cache, pathFragments, requestFormat, request);
        }
    }

    static class DeleteInvoker implements RestProcessingInvoker {
        private final RestServlet servlet;

        DeleteInvoker(final RestServlet servlet) {
            this.servlet = servlet;
        }

        @Override
        public RestResponse process(final RequestCache cache, final String[] pathFragments, final MimeType requestFormat, final String request) throws ResponseException {
            return servlet.restDelete(cache, pathFragments);
        }
    }

    static class ProcessingExceptionTranslator implements TranslatorAware {
        final ResponseStatus status;
        final String message;
        public ProcessingExceptionTranslator(final ProcessingException processingException) {
            this.status = processingException.getStatus();
            this.message = processingException.getMessage();
        }
        @Override
        public void translate(final DataTranslator translator) throws IOException {
            translator.beginObject("response");
            translator.addKeyValue("resultCode", status.getCode());
            translator.addKeyValue("resultMessage", message);
            translator.endObject();
        }
    }
}
