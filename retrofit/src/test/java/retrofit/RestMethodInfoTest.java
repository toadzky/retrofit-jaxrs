// Copyright 2013 Square, Inc.
package retrofit;

import com.google.gson.reflect.TypeToken;
import org.junit.Test;
import retrofit.mime.TypedOutput;
import rx.Observable;

import javax.ws.rs.*;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.fest.assertions.api.Assertions.assertThat;
import static org.junit.Assert.fail;
import static retrofit.RestMethodInfo.ParamUsage.*;
import static retrofit.RestMethodInfo.RequestType.SIMPLE;

public class RestMethodInfoTest
{
  @Test
  public void pathParameterParsing() throws Exception
  {
    expectParams("/");
    expectParams("/foo");
    expectParams("/foo/bar");
    expectParams("/foo/bar/{}");
    expectParams("/foo/bar/{taco}", "taco");
    expectParams("/foo/bar/{t}", "t");
    expectParams("/foo/bar/{!!!}/"); // Invalid parameter.
    expectParams("/foo/bar/{}/{taco}", "taco");
    expectParams("/foo/bar/{taco}/or/{burrito}", "taco", "burrito");
    expectParams("/foo/bar/{taco}/or/{taco}", "taco");
    expectParams("/foo/bar/{taco-shell}", "taco-shell");
    expectParams("/foo/bar/{taco_shell}", "taco_shell");
    expectParams("/foo/bar/{sha256}", "sha256");
    expectParams("/foo/bar/{TACO}", "TACO");
    expectParams("/foo/bar/{taco}/{tAco}/{taCo}", "taco", "tAco", "taCo");
    expectParams("/foo/bar/{1}"); // Invalid parameter, name cannot start with digit.
  }

  private static void expectParams(String path, String... expected)
  {
    Set<String> calculated = RestMethodInfo.parsePathParameters(path);
    assertThat(calculated).hasSize(expected.length);
    if (expected.length > 0)
    {
      assertThat(calculated).containsExactly(expected);
    }
  }

  @Test
  public void pathMustBePrefixedWithSlash()
  {
    class Example
    {
      @GET
      @Path("foo/bar")
      retrofit.client.Response a()
      {
        return null;
      }
    }

    Method method = TestingUtils.getMethod(Example.class, "a");
    RestMethodInfo methodInfo = new RestMethodInfo(method);
    methodInfo.init();
  }

  @Test
  public void noPath()
  {
    class Example
    {
      @GET
      retrofit.client.Response a()
      {
        return null;
      }
    }

    Method method = TestingUtils.getMethod(Example.class, "a");
    RestMethodInfo methodInfo = new RestMethodInfo(method);
    methodInfo.init();
  }


  @Test
  public void concreteCallbackTypes()
  {
    class Example
    {
      @GET
      @Path("/foo")
      void a(ResponseCallback cb)
      {
      }
    }

    Method method = TestingUtils.getMethod(Example.class, "a");
    RestMethodInfo methodInfo = new RestMethodInfo(method);
    assertThat(methodInfo.isSynchronous).isFalse();
    assertThat(methodInfo.responseObjectType).isEqualTo(Response.class);
  }

  @Test
  public void concreteCallbackTypesWithParams()
  {
    class Example
    {
      @GET
      @Path("/foo")
      void a(@QueryParam("id") String id, ResponseCallback cb)
      {
      }
    }

    Method method = TestingUtils.getMethod(Example.class, "a");
    RestMethodInfo methodInfo = new RestMethodInfo(method);
    assertThat(methodInfo.isSynchronous).isFalse();
    assertThat(methodInfo.responseObjectType).isEqualTo(Response.class);
  }

  @Test
  public void genericCallbackTypes()
  {
    class Example
    {
      @GET
      @Path("/foo")
      void a(Callback<Response> cb)
      {
      }
    }

    Method method = TestingUtils.getMethod(Example.class, "a");
    RestMethodInfo methodInfo = new RestMethodInfo(method);
    assertThat(methodInfo.isSynchronous).isFalse();
    assertThat(methodInfo.responseObjectType).isEqualTo(Response.class);
  }

  @Test
  public void genericCallbackTypesWithParams()
  {
    class Example
    {
      @GET
      @Path("/foo")
      void a(@QueryParam("id") String id, Callback<Response> c)
      {
      }
    }

    Method method = TestingUtils.getMethod(Example.class, "a");
    RestMethodInfo methodInfo = new RestMethodInfo(method);
    assertThat(methodInfo.isSynchronous).isFalse();
    assertThat(methodInfo.responseObjectType).isEqualTo(Response.class);
  }

  @Test
  public void wildcardGenericCallbackTypes()
  {
    class Example
    {
      @GET
      @Path("/foo")
      void a(Callback<? extends Response> c)
      {
      }
    }

    Method method = TestingUtils.getMethod(Example.class, "a");
    RestMethodInfo methodInfo = new RestMethodInfo(method);
    assertThat(methodInfo.isSynchronous).isFalse();
    assertThat(methodInfo.responseObjectType).isEqualTo(Response.class);
  }

  @Test
  public void genericCallbackWithGenericType()
  {
    class Example
    {
      @GET
      @Path("/foo")
      void a(Callback<List<String>> c)
      {
      }
    }

    Method method = TestingUtils.getMethod(Example.class, "a");
    RestMethodInfo methodInfo = new RestMethodInfo(method);
    assertThat(methodInfo.isSynchronous).isFalse();

    Type expected = new TypeToken<List<String>>()
    {
    }.getType();
    assertThat(methodInfo.responseObjectType).isEqualTo(expected);
  }

  // RestMethodInfo reconstructs this type from MultimapCallback<String, Set<Long>>. It contains
  // a little of everything: a parameterized type, a generic array, and a wildcard.
  private static Map<? extends String, Set<Long>[]> extendingGenericCallbackType;

  @Test
  public void extendingGenericCallback() throws Exception
  {
    class Example
    {
      @GET
      @Path("/foo")
      void a(MultimapCallback<String, Set<Long>> callback)
      {
      }
    }

    Method method = TestingUtils.getMethod(Example.class, "a");
    RestMethodInfo methodInfo = new RestMethodInfo(method);
    assertThat(methodInfo.isSynchronous).isFalse();
    assertThat(methodInfo.responseObjectType).isEqualTo(
        RestMethodInfoTest.class.getDeclaredField("extendingGenericCallbackType").getGenericType());
  }

  @Test
  public void synchronousResponse()
  {
    class Example
    {
      @GET
      @Path("/foo")
      Response a()
      {
        return null;
      }
    }

    Method method = TestingUtils.getMethod(Example.class, "a");
    RestMethodInfo methodInfo = new RestMethodInfo(method);
    assertThat(methodInfo.isSynchronous).isTrue();
    assertThat(methodInfo.responseObjectType).isEqualTo(Response.class);
  }

  @Test
  public void synchronousGenericResponse()
  {
    class Example
    {
      @GET
      @Path("/foo")
      List<String> a()
      {
        return null;
      }
    }

    Method method = TestingUtils.getMethod(Example.class, "a");
    RestMethodInfo methodInfo = new RestMethodInfo(method);
    assertThat(methodInfo.isSynchronous).isTrue();

    Type expected = new TypeToken<List<String>>()
    {
    }.getType();
    assertThat(methodInfo.responseObjectType).isEqualTo(expected);
  }

  @Test
  public void observableResponse()
  {
    class Example
    {
      @GET
      @Path("/foo")
      Observable<Response> a()
      {
        return null;
      }
    }

    Method method = TestingUtils.getMethod(Example.class, "a");
    RestMethodInfo methodInfo = new RestMethodInfo(method);
    assertThat(methodInfo.isSynchronous).isFalse();
    assertThat(methodInfo.isObservable).isTrue();
    assertThat(methodInfo.responseObjectType).isEqualTo(Response.class);
  }

  @Test
  public void observableGenericResponse()
  {
    class Example
    {
      @GET
      @Path("/foo")
      Observable<List<String>> a()
      {
        return null;
      }
    }

    Method method = TestingUtils.getMethod(Example.class, "a");
    RestMethodInfo methodInfo = new RestMethodInfo(method);
    assertThat(methodInfo.isSynchronous).isFalse();
    assertThat(methodInfo.isObservable).isTrue();
    Type expected = new TypeToken<List<String>>()
    {
    }.getType();
    assertThat(methodInfo.responseObjectType).isEqualTo(expected);
  }

  @Test(expected = IllegalArgumentException.class)
  public void observableWithCallback()
  {
    class Example
    {
      @GET
      @Path("/foo")
      Observable<Response> a(Callback<Response> callback)
      {
        return null;
      }
    }

    Method method = TestingUtils.getMethod(Example.class, "a");
    new RestMethodInfo(method);
  }

  @Test(expected = IllegalArgumentException.class)
  public void missingCallbackTypes()
  {
    class Example
    {
      @GET
      @Path("/foo")
      void a(@QueryParam("id") String id)
      {
      }
    }

    Method method = TestingUtils.getMethod(Example.class, "a");
    new RestMethodInfo(method);
  }

  @Test(expected = IllegalArgumentException.class)
  public void synchronousWithAsyncCallback()
  {
    class Example
    {
      @GET
      @Path("/foo")
      Response a(Callback<Response> callback)
      {
        return null;
      }
    }

    Method method = TestingUtils.getMethod(Example.class, "a");
    new RestMethodInfo(method);
  }

  @Test(expected = IllegalStateException.class)
  public void lackingMethod()
  {
    class Example
    {
      Response a()
      {
        return null;
      }
    }

    Method method = TestingUtils.getMethod(Example.class, "a");
    RestMethodInfo methodInfo = new RestMethodInfo(method);
    methodInfo.init();
  }

  @Test
  public void deleteMethod()
  {
    class Example
    {
      @DELETE
      @Path("/foo")
      Response a()
      {
        return null;
      }
    }

    Method method = TestingUtils.getMethod(Example.class, "a");
    RestMethodInfo methodInfo = new RestMethodInfo(method);
    methodInfo.init();

    assertThat(methodInfo.requestMethod).isEqualTo("DELETE");
    assertThat(methodInfo.requestHasBody).isFalse();
    assertThat(methodInfo.requestUrl).isEqualTo("/foo");
  }

  @Test
  public void getMethod()
  {
    class Example
    {
      @GET
      @Path("/foo")
      Response a()
      {
        return null;
      }
    }

    Method method = TestingUtils.getMethod(Example.class, "a");
    RestMethodInfo methodInfo = new RestMethodInfo(method);
    methodInfo.init();

    assertThat(methodInfo.requestMethod).isEqualTo("GET");
    assertThat(methodInfo.requestHasBody).isFalse();
    assertThat(methodInfo.requestUrl).isEqualTo("/foo");
  }

  @Test
  public void headMethod()
  {
    class Example
    {
      @HEAD
      @Path("/foo")
      Response a()
      {
        return null;
      }
    }

    Method method = TestingUtils.getMethod(Example.class, "a");
    RestMethodInfo methodInfo = new RestMethodInfo(method);
    methodInfo.init();

    assertThat(methodInfo.requestMethod).isEqualTo("HEAD");
    assertThat(methodInfo.requestHasBody).isFalse();
    assertThat(methodInfo.requestUrl).isEqualTo("/foo");
  }

  @Test
  public void postMethod()
  {
    class Example
    {
      @POST
      @Path("/foo")
      Response a()
      {
        return null;
      }
    }

    Method method = TestingUtils.getMethod(Example.class, "a");
    RestMethodInfo methodInfo = new RestMethodInfo(method);
    methodInfo.init();

    assertThat(methodInfo.requestMethod).isEqualTo("POST");
    assertThat(methodInfo.requestHasBody).isTrue();
    assertThat(methodInfo.requestUrl).isEqualTo("/foo");
  }

  @Test
  public void putMethod()
  {
    class Example
    {
      @PUT
      @Path("/foo")
      Response a()
      {
        return null;
      }
    }

    Method method = TestingUtils.getMethod(Example.class, "a");
    RestMethodInfo methodInfo = new RestMethodInfo(method);
    methodInfo.init();

    assertThat(methodInfo.requestMethod).isEqualTo("PUT");
    assertThat(methodInfo.requestHasBody).isTrue();
    assertThat(methodInfo.requestUrl).isEqualTo("/foo");
  }

  @Test
  public void singlePathQueryParam()
  {
    class Example
    {
      @GET
      @Path("/foo?a=b")
      Response a()
      {
        return null;
      }
    }

    Method method = TestingUtils.getMethod(Example.class, "a");
    RestMethodInfo methodInfo = new RestMethodInfo(method);
    methodInfo.init();

    assertThat(methodInfo.requestUrl).isEqualTo("/foo");
    assertThat(methodInfo.requestQuery).isEqualTo("a=b");
  }

  @Test
  public void emptyParams()
  {
    class Example
    {
      @GET
      @Path("/")
      Response a()
      {
        return null;
      }
    }

    Method method = TestingUtils.getMethod(Example.class, "a");
    RestMethodInfo methodInfo = new RestMethodInfo(method);
    methodInfo.init();

    assertThat(methodInfo.requestParamNames).isEmpty();
    assertThat(methodInfo.requestParamUsage).isEmpty();
    assertThat(methodInfo.requestType).isEqualTo(SIMPLE);
  }

  @Test
  public void singlePathParam()
  {
    class Example
    {
      @GET
      @Path("/{a}")
      Response a(@PathParam("a") String a)
      {
        return null;
      }
    }

    Method method = TestingUtils.getMethod(Example.class, "a");
    RestMethodInfo methodInfo = new RestMethodInfo(method);
    methodInfo.init();

    assertThat(methodInfo.requestParamNames).hasSize(1).containsExactly("a");
    assertThat(methodInfo.requestParamUsage).hasSize(1).containsExactly(PATH);
    assertThat(methodInfo.requestType).isEqualTo(SIMPLE);
  }

  @Test
  public void singleQueryParam()
  {
    class Example
    {
      @GET
      @Path("/")
      Response a(@QueryParam("a") String a)
      {
        return null;
      }
    }

    Method method = TestingUtils.getMethod(Example.class, "a");
    RestMethodInfo methodInfo = new RestMethodInfo(method);
    methodInfo.init();

    assertThat(methodInfo.requestParamNames).hasSize(1).containsExactly("a");
    assertThat(methodInfo.requestParamUsage).hasSize(1).containsExactly(QUERY);
    assertThat(methodInfo.requestType).isEqualTo(SIMPLE);
  }

  @Test
  public void multipleQueryParams()
  {
    class Example
    {
      @GET
      @Path("/")
      Response a(@QueryParam("a") String a, @QueryParam("b") String b, @QueryParam("c") String c)
      {
        return null;
      }
    }

    Method method = TestingUtils.getMethod(Example.class, "a");
    RestMethodInfo methodInfo = new RestMethodInfo(method);
    methodInfo.init();

    assertThat(methodInfo.requestParamNames).hasSize(3).containsExactly("a", "b", "c");
    assertThat(methodInfo.requestParamUsage).hasSize(3).containsExactly(QUERY, QUERY, QUERY);
    assertThat(methodInfo.requestType).isEqualTo(SIMPLE);
  }

  @Test
  public void bodyObject()
  {
    class Example
    {
      @PUT
      @Path("/")
      Response a(Object o)
      {
        return null;
      }
    }

    Method method = TestingUtils.getMethod(Example.class, "a");
    RestMethodInfo methodInfo = new RestMethodInfo(method);
    methodInfo.init();

    assertThat(methodInfo.requestParamNames).hasSize(1).containsExactly(new String[]{null});
    assertThat(methodInfo.requestParamUsage).hasSize(1).containsExactly(BODY);
    assertThat(methodInfo.requestType).isEqualTo(SIMPLE);
  }

  @Test
  public void bodyTypedBytes()
  {
    class Example
    {
      @PUT
      @Path("/")
      Response a(TypedOutput o)
      {
        return null;
      }
    }

    Method method = TestingUtils.getMethod(Example.class, "a");
    RestMethodInfo methodInfo = new RestMethodInfo(method);
    methodInfo.init();

    assertThat(methodInfo.requestParamNames).hasSize(1).containsExactly(new String[]{null});
    assertThat(methodInfo.requestParamUsage).hasSize(1).containsExactly(BODY);
    assertThat(methodInfo.requestType).isEqualTo(SIMPLE);
  }

  @Test(expected = IllegalStateException.class)
  public void twoBodies()
  {
    class Example
    {
      @PUT
      @Path("/")
      Response a(int o1, int o2)
      {
        return null;
      }
    }

    Method method = TestingUtils.getMethod(Example.class, "a");
    RestMethodInfo methodInfo = new RestMethodInfo(method);
    methodInfo.init();
  }

  @Test
  public void bodyWithOtherParams()
  {
    class Example
    {
      @PUT
      @Path("/{a}/{c}")
      Response a(@PathParam("a") int a, int b, @PathParam("c") int c)
      {
        return null;
      }
    }
    Method method = TestingUtils.getMethod(Example.class, "a");
    RestMethodInfo methodInfo = new RestMethodInfo(method);
    methodInfo.init();

    assertThat(methodInfo.requestParamNames).containsExactly("a", null, "c");
    assertThat(methodInfo.requestParamUsage).containsExactly(PATH, BODY, PATH);
    assertThat(methodInfo.requestType).isEqualTo(SIMPLE);
  }

  @Test(expected = IllegalStateException.class)
  public void pathParamNonPathParamAndTypedBytes()
  {
    class Example
    {
      @PUT
      @Path("/{a}")
      Response a(@PathParam("a") int a, @PathParam("b") int b, int c)
      {
        return null;
      }
    }

    Method method = TestingUtils.getMethod(Example.class, "a");
    RestMethodInfo methodInfo = new RestMethodInfo(method);
    methodInfo.init();
  }

  @Test(expected = IllegalStateException.class)
  public void parameterWithoutAnnotation()
  {
    class Example
    {
      @GET
      @Path("/")
      Response a(String a)
      {
        return null;
      }
    }

    Method method = TestingUtils.getMethod(Example.class, "a");
    RestMethodInfo methodInfo = new RestMethodInfo(method);
    methodInfo.init();
  }

  @Test(expected = IllegalStateException.class)
  public void nonBodyHttpMethodWithSingleEntity()
  {
    class Example
    {
      @GET
      @Path("/")
      Response a(Object o)
      {
        return null;
      }
    }

    Method method = TestingUtils.getMethod(Example.class, "a");
    RestMethodInfo methodInfo = new RestMethodInfo(method);
    methodInfo.init();
  }

  @Test(expected = IllegalStateException.class)
  public void nonBodyHttpMethodWithTypedBytes()
  {
    class Example
    {
      @GET
      @Path("/")
      Response a(@PathParam("a") TypedOutput a)
      {
        return null;
      }
    }

    Method method = TestingUtils.getMethod(Example.class, "a");
    RestMethodInfo methodInfo = new RestMethodInfo(method);
    methodInfo.init();
  }

  @Test(expected = IllegalStateException.class)
  public void implicitFormEncodingForbidden()
  {
    class Example
    {
      @POST
      @Path("/")
      Response a(@FormParam("a") int a)
      {
        return null;
      }
    }

    Method method = TestingUtils.getMethod(Example.class, "a");
    RestMethodInfo methodInfo = new RestMethodInfo(method);
    methodInfo.init();
  }

  @Test
  public void twoHeaderParams()
  {
    class Example
    {
      @GET
      @Path("/")
      Response a(@HeaderParam("a") String a, @HeaderParam("b") String b)
      {
        return null;
      }
    }

    Method method = TestingUtils.getMethod(Example.class, "a");
    RestMethodInfo methodInfo = new RestMethodInfo(method);
    methodInfo.init();

    assertThat(methodInfo.requestParamNames).containsExactly("a", "b");
    assertThat(methodInfo.requestParamUsage).containsExactly(HEADER, HEADER);
  }

  @Test(expected = IllegalStateException.class)
  public void headerParamMustBeString()
  {
    class Example
    {
      @GET
      @Path("/")
      Response a(@HeaderParam("a") TypedOutput a, @HeaderParam("b") int b)
      {
        return null;
      }
    }

    Method method = TestingUtils.getMethod(Example.class, "a");
    RestMethodInfo methodInfo = new RestMethodInfo(method);
    methodInfo.init();
  }

  @Test
  public void invalidPathParam() throws Exception
  {
    class Example
    {
      @GET
      @Path("/")
      Response a(@PathParam("hey!") String thing)
      {
        return null;
      }
    }

    Method method = TestingUtils.getMethod(Example.class, "a");
    RestMethodInfo methodInfo = new RestMethodInfo(method);
    try
    {
      methodInfo.init();
      fail();
    }
    catch (IllegalStateException e)
    {
      assertThat(e.getMessage()).startsWith("PathParam parameter name is not valid: hey!.");
    }
  }

  private static class Response
  {
  }

  private static interface ResponseCallback extends Callback<Response>
  {
  }

  private static interface MultimapCallback<K, V> extends Callback<Map<? extends K, V[]>>
  {
  }
}
