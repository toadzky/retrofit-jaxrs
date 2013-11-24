/*
 * Copyright (C) 2013 Square, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package retrofit;

import com.google.common.base.CharMatcher;
import rx.Observable;

import javax.ws.rs.*;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.WildcardType;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Request metadata about a service interface declaration.
 */
final class RestMethodInfo
{

  private enum ResponseType
  {
    VOID,
    OBSERVABLE,
    OBJECT
  }

  // Upper and lower characters, digits, underscores, and hyphens, starting with a character.
  private static final String  PARAM            = "[a-zA-Z][a-zA-Z0-9_-]*";
  private static final Pattern PARAM_NAME_REGEX = Pattern.compile(PARAM);
  private static final Pattern PARAM_URL_REGEX  = Pattern.compile("\\{(" + PARAM + ")\\}");

  enum ParamUsage
  {
    PATH, ENCODED, QUERY, FIELD, PART, BODY, HEADER
  }

  enum RequestType
  {
    /**
     * No content-specific logic required.
     */
    SIMPLE,
    /**
     * Multi-part request body.
     */
    MULTIPART,
    /**
     * Form URL-encoded request body.
     */
    FORM_URL_ENCODED
  }

  final Method method;

  boolean loaded = false;

  // Method-level details
  final ResponseType responseType;
  final boolean      isSynchronous;
  final boolean      isObservable;
  Type responseObjectType;
  RequestType requestType = RequestType.SIMPLE;
  String                       requestMethod;
  boolean                      requestHasBody;
  String                       requestUrl;
  Set<String>                  requestUrlParamNames;
  String                       requestQuery;
  List<retrofit.client.Header> headers;

  // Parameter-level details
  String[]     requestParamNames;
  ParamUsage[] requestParamUsage;

  RestMethodInfo(Method method)
  {
    this.method = method;
    responseType = parseResponseType();
    isSynchronous = (responseType == ResponseType.OBJECT);
    isObservable = (responseType == ResponseType.OBSERVABLE);
  }

  synchronized void init()
  {
    if (loaded)
    {
      return;
    }

    parseMethodAnnotations();
    parseParameters();

    loaded = true;
  }

  /**
   * Loads {@link #requestMethod} and {@link #requestType}.
   */
  private void parseMethodAnnotations()
  {
    Path classPath = method.getDeclaringClass().getAnnotation(Path.class);
    Path methodPath = method.getAnnotation(Path.class);
    for (Annotation methodAnnotation : method.getAnnotations())
    {
      Class<? extends Annotation> annotationType = methodAnnotation.annotationType();
      HttpMethod methodInfo = null;

      // Look for a @HttpMethod annotation on the parameter annotation indicating request method.
      for (Annotation innerAnnotation : annotationType.getAnnotations())
      {
        if (HttpMethod.class == innerAnnotation.annotationType())
        {
          methodInfo = (HttpMethod) innerAnnotation;
          break;
        }
      }

      if (methodInfo != null)
      {
        if (requestMethod != null)
        {
          throw new IllegalArgumentException("Method "
                                             + method.getName()
                                             + " contains multiple HTTP methods. Found: "
                                             + requestMethod
                                             + " and "
                                             + methodInfo.value());
        }
        String pathStr = classPath == null ? "" : CharMatcher.is('/').trimTrailingFrom(classPath.value());
        if (methodPath != null)
        {
          if (!methodPath.value().startsWith("/"))
          {
            pathStr += "/";
          }
          pathStr += methodPath.value();
        }
        parsePath(pathStr);
        requestMethod = methodInfo.value();
        requestHasBody = Utils.requestAllowsBody(methodInfo);
      }
    }

    if (requestMethod == null)
    {
      throw new IllegalStateException(
          "Method " + method.getName() + " not annotated with request type (e.g., GET, POST).");
    }
    if (!requestHasBody)
    {
      if (requestType == RequestType.FORM_URL_ENCODED)
      {
        throw new IllegalStateException(
            "Form URL Encoded bodies can only be specific on HTTP methods with request body (e.g., POST). ("
            + method.getName()
            + ")");
      }
    }
  }

  /**
   * Loads {@link #requestUrl}, {@link #requestUrlParamNames}, and {@link #requestQuery}.
   */
  private void parsePath(String path)
  {
    if (path == null || (path.length() > 0 && !path.startsWith("/")))
    {
      throw new IllegalArgumentException("URL path \""
                                         + path
                                         + "\" on method "
                                         + method.getName()
                                         + " must start with '/'. ("
                                         + method.getName()
                                         + ")");
    }

    // Get the relative URL path and existing query string, if present.
    String url = path;
    String query = null;
    int question = path.indexOf('?');
    if (question != -1 && question < path.length() - 1)
    {
      url = path.substring(0, question);
      query = path.substring(question + 1);

      // Ensure the query string does not have any named parameters.
      Matcher queryParamMatcher = PARAM_URL_REGEX.matcher(query);
      if (queryParamMatcher.find())
      {
        throw new IllegalStateException("URL query string \""
                                        + query
                                        + "\" on method "
                                        + method.getName()
                                        + " may not have replace block.");
      }
    }

    Set<String> urlParams = parsePathParameters(path);

    requestUrl = url;
    requestUrlParamNames = urlParams;
    requestQuery = query;
  }

  private List<retrofit.client.Header> parseHeaders(String[] headers)
  {
    List<retrofit.client.Header> headerList = new ArrayList<retrofit.client.Header>();
    for (String header : headers)
    {
      int colon = header.indexOf(':');
      if (colon == -1 || colon == 0 || colon == header.length() - 1)
      {
        throw new IllegalStateException(
            "HeaderParam must be in the form \"Name: Value\": \"" + header + "\"");
      }
      headerList.add(new retrofit.client.Header(header.substring(0, colon),
                                                header.substring(colon + 1).trim()));
    }
    return headerList;
  }

  /**
   * Loads {@link #responseObjectType}. Returns {@code true} if method is synchronous.
   */
  private ResponseType parseResponseType()
  {
    // Synchronous methods have a non-void return type.
    // Observable methods have a return type of Observable.
    Type returnType = method.getGenericReturnType();

    // Asynchronous methods should have a Callback type as the last argument.
    Type lastArgType = null;
    Class<?> lastArgClass = null;
    Type[] parameterTypes = method.getGenericParameterTypes();
    if (parameterTypes.length > 0)
    {
      Type typeToCheck = parameterTypes[parameterTypes.length - 1];
      lastArgType = typeToCheck;
      if (typeToCheck instanceof ParameterizedType)
      {
        typeToCheck = ((ParameterizedType) typeToCheck).getRawType();
      }
      if (typeToCheck instanceof Class)
      {
        lastArgClass = (Class<?>) typeToCheck;
      }
    }

    boolean hasReturnType = returnType != void.class;
    boolean hasCallback = lastArgClass != null && Callback.class.isAssignableFrom(lastArgClass);

    // Check for invalid configurations.
    if (hasReturnType && hasCallback)
    {
      throw new IllegalArgumentException("Method "
                                         + method.getName()
                                         + " may only have return type or Callback as last argument, not both.");
    }
    if (!hasReturnType && !hasCallback)
    {
      throw new IllegalArgumentException("Method "
                                         + method.getName()
                                         + " must have either a return type or Callback as last argument.");
    }

    if (hasReturnType)
    {
      if (Platform.HAS_RX_JAVA)
      {
        Class rawReturnType = Types.getRawType(returnType);
        if (rawReturnType == Observable.class)
        {
          returnType = Types.getSupertype(returnType, rawReturnType, Observable.class);
          responseObjectType = getParameterUpperBound((ParameterizedType) returnType);
          return ResponseType.OBSERVABLE;
        }
      }
      responseObjectType = returnType;
      return ResponseType.OBJECT;
    }

    lastArgType = Types.getSupertype(lastArgType, Types.getRawType(lastArgType), Callback.class);
    if (lastArgType instanceof ParameterizedType)
    {
      responseObjectType = getParameterUpperBound((ParameterizedType) lastArgType);
      return ResponseType.VOID;
    }

    throw new IllegalArgumentException("Last parameter of "
                                       + method.getName()
                                       + " must be of type Callback<X> or Callback<? super X>. Found: "
                                       + lastArgType);
  }


  private static Type getParameterUpperBound(ParameterizedType type)
  {
    Type[] types = type.getActualTypeArguments();
    for (int i = 0; i < types.length; i++)
    {
      Type paramType = types[i];
      if (paramType instanceof WildcardType)
      {
        types[i] = ((WildcardType) paramType).getUpperBounds()[0];
      }
    }
    return types[0];
  }

  /**
   * Loads {@link #requestParamNames} and {@link #requestParamUsage}. Must be called after
   * {@link #parseMethodAnnotations()}.
   */
  private void parseParameters()
  {
    Class<?>[] parameterTypes = method.getParameterTypes();

    Annotation[][] parameterAnnotationArrays = method.getParameterAnnotations();
    int count = parameterAnnotationArrays.length;
    if (!isSynchronous && !isObservable)
    {
      count -= 1; // Callback is last argument when not a synchronous method.
    }

    String[] paramNames = new String[count];
    requestParamNames = paramNames;
    ParamUsage[] paramUsage = new ParamUsage[count];
    requestParamUsage = paramUsage;

    boolean gotField = false;
    boolean gotPart = false;
    boolean gotBody = false;

    for (int i = 0; i < count; i++)
    {
      Class<?> parameterType = parameterTypes[i];
      Annotation[] parameterAnnotations = parameterAnnotationArrays[i];
      if (parameterAnnotations != null && parameterAnnotations.length != 0)
      {
        for (Annotation parameterAnnotation : parameterAnnotations)
        {
          Class<? extends Annotation> annotationType = parameterAnnotation.annotationType();

          if (annotationType == PathParam.class)
          {
            String name = ((PathParam) parameterAnnotation).value();
            validatePathName(name);

            paramNames[i] = name;
            paramUsage[i] = ParamUsage.PATH;
          }
          else if (annotationType == QueryParam.class)
          {
            String name = ((QueryParam) parameterAnnotation).value();

            paramNames[i] = name;
            paramUsage[i] = ParamUsage.QUERY;
          }
          else if (annotationType == HeaderParam.class)
          {
            String name = ((HeaderParam) parameterAnnotation).value();
            if (parameterType != String.class)
            {
              throw new IllegalStateException("@HeaderParam parameter type must be String: " + name);
            }

            paramNames[i] = name;
            paramUsage[i] = ParamUsage.HEADER;
          }
          else if (annotationType == FormParam.class)
          {
            if (requestType != RequestType.FORM_URL_ENCODED)
            {
              throw new IllegalStateException(
                  "@FormParam parameters can only be used with form encoding.");
            }

            String name = ((FormParam) parameterAnnotation).value();

            gotField = true;
            paramNames[i] = name;
            paramUsage[i] = ParamUsage.FIELD;
          }
        }
      }
      // For body parameter
      else
      {
        if (gotBody)
        {
          throw new IllegalStateException(
              "Method has multiple body parameters: " + method);
        }

        paramUsage[i] = ParamUsage.BODY;
        gotBody = true;
      }

      if (paramUsage[i] == null)
      {
        throw new IllegalStateException(
            "No Retrofit annotation found on parameter " + (i + 1) + " of " + method.getName());
      }
    }

    if (requestType == RequestType.SIMPLE && !requestHasBody && gotBody)
    {
      throw new IllegalStateException("Non-body HTTP method cannot contain a body or @TypedOutput.");
    }
    if (requestType == RequestType.FORM_URL_ENCODED && !gotField)
    {
      throw new IllegalStateException("Form-encoded method must contain at least one @FormParam.");
    }
    if (requestType == RequestType.MULTIPART && !gotPart)
    {
      throw new IllegalStateException("Multipart method must contain at least one @Part.");
    }
  }

  private void validatePathName(String name)
  {
    if (!PARAM_NAME_REGEX.matcher(name).matches())
    {
      throw new IllegalStateException("PathParam parameter name is not valid: "
                                      + name
                                      + ". Must match "
                                      + PARAM_URL_REGEX.pattern());
    }
    // Verify URL replacement name is actually present in the URL path.
    if (!requestUrlParamNames.contains(name))
    {
      throw new IllegalStateException(
          "Method URL \"" + requestUrl + "\" does not contain {" + name + "}.");
    }
  }

  /**
   * Gets the set of unique path parameters used in the given URI. If a parameter is used twice
   * in the URI, it will only show up once in the set.
   */
  static Set<String> parsePathParameters(String path)
  {
    Matcher m = PARAM_URL_REGEX.matcher(path);
    Set<String> patterns = new LinkedHashSet<String>();
    while (m.find())
    {
      patterns.add(m.group(1));
    }
    return patterns;
  }
}
