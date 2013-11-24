// Copyright 2013 Square, Inc.
package retrofit;

import org.junit.Before;
import org.junit.Test;

import java.util.concurrent.Executor;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.same;
import static org.mockito.Mockito.*;
import static retrofit.Utils.SynchronousExecutor;

public class CallbackRunnableTest
{
  private Executor executor = spy(new SynchronousExecutor());
  private CallbackRunnable<Object> callbackRunnable;
  private Callback<Object>         callback;

  @Before
  public void setUp()
  {
    callback = mock(Callback.class);
    callbackRunnable = spy(new CallbackRunnable<Object>(callback, executor)
    {
      @Override
      public ResponseWrapper obtainResponse()
      {
        return null; // Must be mocked.
      }
    });
  }

  @Test
  public void responsePassedToSuccess()
  {
    ResponseWrapper wrapper = new ResponseWrapper(null, new Object());
    when(callbackRunnable.obtainResponse()).thenReturn(wrapper);

    callbackRunnable.run();

    verify(executor).execute(any(Runnable.class));
    verify(callback).success(same(wrapper.responseBody), same(wrapper.response));
  }

  @Test
  public void errorPassedToFailure()
  {
    RetrofitError exception = RetrofitError.unexpectedError("", null);
    when(callbackRunnable.obtainResponse()).thenThrow(exception);

    callbackRunnable.run();

    verify(executor).execute(any(Runnable.class));
    verify(callback).failure(same(exception));
  }
}
