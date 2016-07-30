package cn.yxffcode.mybatisbatch;

import cn.yxffcode.mybatisbatch.utils.Reflections;
import com.google.common.base.Throwables;
import org.apache.ibatis.executor.BatchExecutor;
import org.apache.ibatis.executor.BatchResult;
import org.apache.ibatis.executor.CachingExecutor;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Plugin;
import org.apache.ibatis.plugin.Signature;
import org.apache.ibatis.session.Configuration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 * 方便使用的批量更新插件,只需要sql statement id以batch开头,参数为Iterable或者数组即可.
 * <p>
 * 限制:最好是作为第一个拦截器使用,因为在它之前的拦截器不会被调用
 *
 * @author gaohang on 16/7/29.
 */
@Intercepts({
        @Signature(type = Executor.class, method = "update", args = {MappedStatement.class, Object.class}),
})
public class BatchExecutorInterceptor implements Interceptor {

  private static final Logger LOGGER = LoggerFactory.getLogger(BatchExecutorInterceptor.class);

  @Override
  public Object intercept(final Invocation invocation) throws Throwable {
    //check argument
    if (invocation.getArgs()[1] == null) {
      return invocation.proceed();
    }

    final MappedStatement ms = (MappedStatement) invocation.getArgs()[0];

    //if it should use batch
    if (!shouldDoBatch(ms.getId())) {
      return invocation.proceed();
    }

    //create batch executor
    final Executor targetExecutor = getTargetExecutor(invocation);
    if (targetExecutor instanceof BatchExecutor) {
      return invocation.proceed();
    }
    final Configuration configuration = (Configuration) Reflections.getField("configuration", targetExecutor);

    final BatchExecutor batchExecutor = new BatchExecutorWrapper(configuration, targetExecutor.getTransaction());
    return batchExecutor.update(ms, invocation.getArgs()[1]);
  }

  private Executor getTargetExecutor(final Invocation invocation) {
    Executor targetExecutor = (Executor) invocation.getTarget();
    while (targetExecutor instanceof Proxy) {
      targetExecutor = (Executor) Reflections.getField("target",
              Proxy.getInvocationHandler(targetExecutor));
    }
    //取真正的executor
    if (targetExecutor instanceof CachingExecutor) {
      targetExecutor = (Executor) Reflections.getField("delegate", targetExecutor);
    }
    return targetExecutor;
  }

  private boolean shouldDoBatch(final String statementId) {
    return statementId.startsWith("batch", statementId.lastIndexOf('.') + 1);
  }

  @Override
  public Object plugin(final Object target) {
    if (!(target instanceof Executor)) {
      return target;
    }
    if (target instanceof BatchExecutor) {
      return target;
    }

    return Plugin.wrap(target, this);
  }

  @Override
  public void setProperties(final Properties properties) {
  }
}
