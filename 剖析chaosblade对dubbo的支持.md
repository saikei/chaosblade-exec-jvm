​	一开始是打算从几个大的方面（插件化、切面、增强器）展开，但思考了一下，会不会以一个探索的角度穿插这几个模块去重现一个剖析流程会更好呢？

# chaosblade简单了解

​	（之前有了解过的话可以直接超过）首先大概介绍一下chaosblade，阿里开源的一个故障注入工具用于支持混沌工程的实验开展。github：https://github.com/chaosblade-io/chaosblade

- 目前主要支持的故障注入类型：

  ![](https://user-images.githubusercontent.com/3992234/72340872-eb47c400-3703-11ea-830f-062e117c2e95.png)

- 主执行器，也就是对应上面给出的github地址，是golang配合cobra（cli框架）实现

- 整个工具基于插件化的一个思想，而我们这次分析的dubbo支持插件位于chaosblade-exec-jvm模块下面（java实现）

- 故障注入的核心思想（可以配合混沌工程实验流程理解）：

  - 在一个围绕稳态的条件下（有可观测的指标metric）
  - 找到你想注入的一个target（有可能是一个类，一个方法，一个节点）
  - 类或者方法的话就是在加载或者执行的前后注入例如延迟、异常的动作，节点的话就是杀、停
  - 然后观察业务指标的变动，观察实验的效果
  - 并进行记录分析

- jvm对切面的实现的简单了解：

  - 而jvm对dubbo的支持也是基于刚才提到的思想，但是我们需要一个工具供以我们做一个aop的操作，aop的话你是不是此时会想起大名鼎鼎的springaop，抱歉，springaop虽然强大，但是有一个比较大的阻碍，做aop的环境必须是一个spring的容器。

  - 那么有没有一个不需要spring容器支持的aop工具？
  - 其实是有的，业界会有btrace（一般用于检测线上故障），jvmsandbox（一种*JVM*的非侵入式运行期AOP解决方案，阿里开源的动态注入工具，你可以理解为btrace的增强版吧）
  - 上面两个都是基于javaagent动态代理机制和动态attach挂载进程机制实现，有兴趣可以了解一下
  - **而chaosblade主要调用流程为：chaosblade主执行器解析命令-> 以http形式去调用jvmsandbox（内置一个jetty）-> sandbox已经动态挂载并动态注入**

- 最后简单看下chaosblade或者说混沌实验模型

  ![](https://raw.githubusercontent.com/saikei/learn/master/img/20200322144438.png)

  **下面代码架构与此处关系很大**

# 代码架构简析

我们先纵观看下代码结构，接下来会一一分析

![](https://raw.githubusercontent.com/saikei/learn/master/img/20200322133337.png)

- 主要分为四个部分

  - 支持consumer的模块

  - model包，主要有一大堆的matcher匹配规则，还有一个额外的支持DubboThreadPoolFullExecutor执行器（模拟线程池满的场景）

    - 这里我们可以看一下，cli命令的flags,其实就是对应上文的每个matcherModel（有些公共的matcherModel不在此处，例如offset），

    ```
    Usage:
      blade create dubbo delay
    
    Flags:
          --appname string      The consumer or provider application name
          --consumer            To tag consumer role experiment.
      -h, --help                help for delay
          --methodname string   The method name in service interface
          --offset string       delay offset for the time
          --process string      Application process name
          --provider            To tag provider experiment
          --service string      The service interface
          --time string         delay time (required)
          --version string      the service version
    
    Global Flags:
      -d, --debug   Set client to DEBUG mode
    ```

    - 可以打开一个model看看，其实就是对cli参数的匹配

      ```java
      public class ConsumerMatcherSpec extends BasePredicateMatcherSpec {
      
          @Override
          public String getName() {
              return DubboConstant.CONSUMER_KEY;
          }
      
          @Override
          public String getDesc() {
              return "To tag consumer role experiment.";
          }
      
          @Override
          public boolean noArgs() {
              return true;
          }//由于只匹配有无这个参数
      
          @Override
          public boolean required() {
              return false;
          }
      }
      ```

  - 支持provider的模块

  - 一些公共的抽象方法，代码的聚合吧

- 三个部分

  - 插件的注册，上文说到chaosblade基于插件化的思想比较明显
  - 切面，以PointCut结尾的都是对切面aop的支持
  - 增强器，进行匹配规则

- 接着看下其中一个Dubbo的模型吧，其实就是对cli的dubbo模型实现

  ```java
  public class DubboModelSpec extends FrameworkModelSpec implements PreCreateInjectionModelHandler,
      PreDestroyInjectionModelHandler {
  
      public DubboModelSpec() {
          super();
          addThreadPoolFullActionSpec();
      }
  
      @Override
      public String getShortDesc() {
          return "dubbo experiment";
      }
  
      @Override
      public String getLongDesc() {
          return "Dubbo experiment for testing service delay and exception.";
      }
  
      @Override
      public String getExample() {
          return "dubbo delay --time 3000 --consumer --service com.example.service.HelloService";
      }
  
      @Override
          //添加匹配规则
      protected List<MatcherSpec> createNewMatcherSpecs() {
          ArrayList<MatcherSpec> matcherSpecs = new ArrayList<MatcherSpec>();
          matcherSpecs.add(new ConsumerMatcherSpec());
          matcherSpecs.add(new ProviderMatcherSpec());
          matcherSpecs.add(new AppNameMatcherDefSpec());
          matcherSpecs.add(new ServiceMatcherSpec());
          matcherSpecs.add(new VersionMatcherSpec());
          matcherSpecs.add(new MethodNameMatcherSpec());
          matcherSpecs.add(new GroupMatcherSpec());
          return matcherSpecs;
      }
  
      @Override
          //断言结果
      protected PredicateResult preMatcherPredicate(Model model) {
          if (model == null) {
              return PredicateResult.fail("matcher not found for dubbo");
          }
          MatcherModel matcher = model.getMatcher();
          Set<String> keySet = matcher.getMatchers().keySet();
          for (String key : keySet) {
              //查询参数consumer或者provider是否存在，即cli的--consumer --provider
              if (key.equals(DubboConstant.CONSUMER_KEY) || key.equals(DubboConstant.PROVIDER_KEY)) {
                  return PredicateResult.success();
              }
          }
          return PredicateResult.fail("less necessary matcher is consumer or provider for dubbo");
      }
  
      @Override
      public String getTarget() {
          return DubboConstant.TARGET_NAME;//“dubbo"
      }
  
      @Override
      public void preCreate(String suid, Model model) throws ExperimentException {
          if (ThreadPoolFullActionSpec.NAME.equals(model.getActionName())) {
              ActionSpec actionSpec = this.getActionSpec(model.getActionName());
              ActionExecutor actionExecutor = actionSpec.getActionExecutor();
              //对threadPoolFullExecuter的注册
              if (actionExecutor instanceof DubboThreadPoolFullExecutor) {
                  DubboThreadPoolFullExecutor threadPoolFullExecutor = (DubboThreadPoolFullExecutor)actionExecutor;
                  threadPoolFullExecutor.setExpReceived(true);
              } else {
                  throw new ExperimentException("actionExecutor is not instance of DubboThreadPoolFullExecutor");
              }
          }
      }
  
      @Override
          //销毁，suid是实验的id，go端会维护一个sqllite本地数据库
      public void preDestroy(String suid, Model model) throws ExperimentException {
          if (ThreadPoolFullActionSpec.NAME.equals(model.getActionName())) {
              ActionSpec actionSpec = this.getActionSpec(model.getActionName());
              ActionExecutor actionExecutor = actionSpec.getActionExecutor();
              if (actionExecutor instanceof DubboThreadPoolFullExecutor) {
                  DubboThreadPoolFullExecutor threadPoolFullExecutor = (DubboThreadPoolFullExecutor)actionExecutor;
                  threadPoolFullExecutor.revoke();
              } else {
                  throw new ExperimentException("actionExecutor is not instance of DubboThreadPoolFullExecutor");
              }
          }
      }
  
  }
  ```

# 切面

- 我们先从切面来看，分析consumer为主，provider也是类似的，只不过选点不一样

  ```java
  //其实此处主要做了两个规则匹配，一个是类，一个是方法
  public class DubboConsumerPointCut implements PointCut {
  
      @Override
      //对类限定名进行匹配
      public ClassMatcher getClassMatcher() {
          OrClassMatcher classMatcher = new OrClassMatcher();
          classMatcher
              .or(new NameClassMatcher("com.alibaba.dubbo.rpc.protocol.dubbo.DubboInvoker"))
              .or(new NameClassMatcher("com.alibaba.dubbo.rpc.protocol.thrift.ThriftInvoker"))
              .or(new NameClassMatcher("com.alibaba.dubbo.rpc.protocol.dubbo.ChannelWrappedInvoker"))
  
              .or(new NameClassMatcher("org.apache.dubbo.rpc.protocol.dubbo.DubboInvoker"))
              .or(new NameClassMatcher("org.apache.dubbo.rpc.protocol.thrift.ThriftInvoker"))
              .or(new NameClassMatcher("org.apache.dubbo.rpc.protocol.dubbo.ChannelWrappedInvoker"));
          return classMatcher;
      }
  
      @Override
      //对方法进行匹配，方法其实就是doInvoke(Invocation invocation)
      //invocation可以理解为是invoker的上下文，下面匹配也会有用
      public MethodMatcher getMethodMatcher() {
          AndMethodMatcher methodMatcher = new AndMethodMatcher();
          ParameterMethodMatcher parameterMethodMatcher = new ParameterMethodMatcher(new String[] {
              "com.alibaba.dubbo.rpc.Invocation"}, 1,
              ParameterMethodMatcher.EQUAL);
          methodMatcher.and(new NameMethodMatcher("doInvoke")).and(parameterMethodMatcher);
  
          AndMethodMatcher methodMatcherThan2700 = new AndMethodMatcher();
          ParameterMethodMatcher parameterMethodMatcherThan2700 = new ParameterMethodMatcher(new String[] {
              "org.apache.dubbo.rpc.Invocation"}, 1,
              ParameterMethodMatcher.EQUAL);
          methodMatcherThan2700.and(new NameMethodMatcher("doInvoke")).and(parameterMethodMatcherThan2700);
  
          return new OrMethodMatcher().or(methodMatcher).or(methodMatcherThan2700);
      }
  }
  
  ```

  应该就是这三个方法：

  ```java
  com.alibaba.dubbo.rpc.protocol.dubbo.DubboInvoker$doInvole(com.alibaba.dubbo.rpc.Invocation invacation)
  com.alibaba.dubbo.rpc.protocol.thrift.ThriftInvoker$doInvole(com.alibaba.dubbo.rpc.Invocation invacation)
  com.alibaba.dubbo.rpc.protocol.dubbo.ChannelWrappedInvoker$doInvole(com.alibaba.dubbo.rpc.Invocation invacation)
  ```

- 不知道你有没有一个疑问，为什么切这三个点呢？

  - 我们可以想一下实验到底要做什么？
  - 调用延迟：这个好办，只要在调用链上，比较核心的调用方法处进行切就可以了
  - 抛异常：这个异常你不能瞎抛啊！你要接近真实场景，模拟出真实场景的异常

- 宏观看一下，dubbo的核心（rpc），consumer去远程调用provider的方法，先看下dubbo的架构

  ![](https://dubbo.apache.org/img/blog/rpc/rpc-work-principle.png)

  ![](https://raw.githubusercontent.com/saikei/learn/master/img/Inked%E4%BC%81%E4%B8%9A%E5%BE%AE%E4%BF%A1%E6%88%AA%E5%9B%BE_158485673422_LI.jpg)

  - 这里是dubbo的架构图，左边圈住的consumer的切点，位于协议层，右边圈住的是provider的切点，位于代理层

  - 这从大体上不难理解，切面切的还是主要的invoker调用的方法，而consumer真正调用的地方（抛开上面的集群选机器，注册目录，抛开下层的交换传输），那么这个点应该就是在协议层了（协议层一般用于协议的二次编码），provider的话同理

    ![](https://raw.githubusercontent.com/saikei/learn/master/img/20200322141353.png)

- 为什么切在doInvoke方法呢？ -> 真实抛出异常的地方

  - 对比下面两个调用方法，上面的异常明显被catch掉，而下面才是真正抛异常的地方，所在在下面抛异常应该更符合业务逻辑场景

  - ```java
    //类org.apache.dubbo.rpc.protocol.AbstractInvoker
    /**
     * 大部分代码用于添加信息到 RpcInvocation#attachment 变量
     */
    public Result invoke(Invocation inv) throws RpcException {
        //省略无数代码。。。
        try {
                //invoke的地方
                asyncResult = (AsyncRpcResult) doInvoke(invocation);
            } catch (InvocationTargetException e) { // biz exception
                Throwable te = e.getTargetException();
                if (te == null) {
                    asyncResult = AsyncRpcResult.newDefaultAsyncResult(null, e, invocation);
                } else {
                    if (te instanceof RpcException) {
                        ((RpcException) te).setCode(RpcException.BIZ_EXCEPTION);
                    }
                    asyncResult = AsyncRpcResult.newDefaultAsyncResult(null, te, invocation);
                }
            } catch (RpcException e) {
                if (e.isBiz()) {
                    asyncResult = AsyncRpcResult.newDefaultAsyncResult(null, e, invocation);
                } else {
                    throw e;
                }
            } catch (Throwable e) {
                asyncResult = AsyncRpcResult.newDefaultAsyncResult(null, e, invocation);
            }
        RpcContext.getContext().setFuture(new FutureAdapter(asyncResult.getResponseFuture()));
        return asyncResult;
    }
    
    //org.apache.dubbo.rpc.protocol.dubbo.DubboInvoker
    @Override
        protected Result doInvoke(final Invocation invocation) throws Throwable {
            //省略无数代码...
            //不得不插一句，此处CompletableFuture改造后，代码逻辑没见得清晰了
            } catch (TimeoutException e) {
                throw new RpcException(RpcException.TIMEOUT_EXCEPTION, "Invoke remote method timeout. method: " + invocation.getMethodName() + ", provider: " + getUrl() + ", cause: " + e.getMessage(), e);
            } catch (RemotingException e) {
                throw new RpcException(RpcException.NETWORK_EXCEPTION, "Failed to invoke remote method: " + invocation.getMethodName() + ", provider: " + getUrl() + ", cause: " + e.getMessage(), e);
            }
        }
    ```

- provider的话切在org.apache.dubbo.rpc.proxy.AbstractProxyInvoker同理，异常选点，不多加分析

# Enhancer增强器

- 其实基本切面分析完了，大概重点就完了，接下来就是对参数的匹配

- 主要分析DubboEnhancer公共抽象类以及DubboConsumerEnhancer

  - ![](https://raw.githubusercontent.com/saikei/learn/master/img/20200322143547.png)

  - 详情可以看注释即可

  ```java
  //由于这部分consumer和provider逻辑是一样的，为了聚合，被抽出来作为公共方法
  @Override
  public EnhancerModel doBeforeAdvice(ClassLoader classLoader, String className, Object object,
                                      Method method, Object[]
                                          methodArguments)
      throws Exception {
      //此处用于支持fullthreadpool，不加分析
      if (method.getName().equals(RECEIVED_METHOD)) {
          // received method for thread pool experiment
          DubboThreadPoolFullExecutor.INSTANCE.setWrappedChannelHandler(object);
          return null;
      }
  
      //反射获取刚才对应方法的invocation上下文，里面会包含Dubbo的Url，url基本包含了cli端输入参数的大部分信息
      Object invocation = methodArguments[0];
      if (object == null || invocation == null) {
          LOGGER.warn("The necessary parameter is null.");
          return null;
      }
      //获取Dubbo的URL对象
      Object url = getUrl(object, invocation);
      if (url == null) {
          LOGGER.warn("Url is null, can not get necessary values.");
          return null;
      }
      //通过URL对象获取appName
      String appName = ReflectUtil.invokeMethod(url, GET_PARAMETER, new Object[] {APPLICATION_KEY}, false);
      //通过invocation获取方法名
      String methodName = ReflectUtil.invokeMethod(invocation, GET_METHOD_NAME, new Object[0], false);
      String[] serviceAndVersionGroup = getServiceNameWithVersionGroup(invocation, url);
  
      //构造匹配模型，其实就是cli输入参数的匹配模型
      MatcherModel matcherModel = new MatcherModel();
      matcherModel.add(DubboConstant.APP_KEY, appName);
      matcherModel.add(DubboConstant.SERVICE_KEY, serviceAndVersionGroup[0]);
      matcherModel.add(DubboConstant.VERSION_KEY, serviceAndVersionGroup[1]);
      if (2 < serviceAndVersionGroup.length &&
          null != serviceAndVersionGroup[2]) {
          matcherModel.add(DubboConstant.GROUP_KEY, serviceAndVersionGroup[2]);
      }
      matcherModel.add(DubboConstant.METHOD_KEY, methodName);
      int timeout = getTimeout(methodName, object, invocation);
      matcherModel.add(DubboConstant.TIMEOUT_KEY, timeout + "");
  
      if (LOGGER.isDebugEnabled()) {
          LOGGER.debug("dubbo matchers: {}", JSON.toJSONString(matcherModel));
      }
  
      //可以理解为匹配模型的一个适配吧，加上classLoader（一会用于加载实例）
      EnhancerModel enhancerModel = new EnhancerModel(classLoader, matcherModel);
      //延迟的执行器，主要这里还是传入对应的异常，例如Dubbo的话对应就是rpcException
      //如果是gRpc就是gRpc的异常
      enhancerModel.setTimeoutExecutor(createTimeoutExecutor(classLoader, timeout, className));
  
      //最后添加的仅仅是对comsumer和provider的匹配，就是cli对应的--consumer / --provider
      postDoBeforeAdvice(enhancerModel);
      return enhancerModel;
  }
  
  protected abstract void postDoBeforeAdvice(EnhancerModel enhancerModel);
  
  //consumer的
  @Override
      protected void postDoBeforeAdvice(EnhancerModel enhancerModel) {
          enhancerModel.addMatcher(DubboConstant.CONSUMER_KEY, "true");
      }
  ```



# 注入

- 上面的话，其实对于一个rpc框架实践的核心基本分析完了

- 这里是分析注入真正的地方，串联起来，更容易理解

- 直接放代码，这里位于**BeforeEnhancer**，有没有感到熟悉的感觉，就是DubboEnhancer的上层调用方

  ```java
  public abstract class BeforeEnhancer implements Enhancer {
  
      /**
       * Do fault-inject
       *
       * @param targetName      the plugin target name
       * @param classLoader     classloader for the class
       * @param className       the class name
       * @param object          the class instance. Value is null if the method is static
       * @param method          the class method
       * @param methodArguments the method arguments
       * @throws Exception
       */
      @Override
      public void beforeAdvice(String targetName, ClassLoader classLoader, String className, Object object,
                               Method method, Object[] methodArguments) throws Exception {
          if (!ManagerFactory.getStatusManager().expExists(targetName)) {
              return;
          }
          //这里的doBeforeAdvice就是多态下DubboEnhancer的方法
          EnhancerModel model = doBeforeAdvice(classLoader, className, object, method, methodArguments);
          if (model == null) {
              return;
          }
          model.setTarget(targetName).setMethod(method).setObject(object).setMethodArguments(methodArguments);
          //真正注入的地方
          Injector.inject(model);
      }
  ```

  这里是真正注入的地方

  ```java
  public class Injector {
      private static final Logger LOGGER = LoggerFactory.getLogger(Injector.class);
  
      /**
       * Inject
       *
       * @param enhancerModel
       * @throws InterruptProcessException
       */
      public static void inject(EnhancerModel enhancerModel) throws InterruptProcessException {
          //利用target就是"dubbo"，还记得之前set的target吗
          String target = enhancerModel.getTarget();
          /**
          StatusMetric的字段，记录实验状态，hitCounts记录实验次数
          	private Model model;
      		private AtomicLong hitCounts;
     			 private Lock lock = new ReentrantLock();
          **/
          List<StatusMetric> statusMetrics = ManagerFactory.getStatusManager().getExpByTarget(
              target);
          for (StatusMetric statusMetric : statusMetrics) {
              Model model = statusMetric.getModel();
              if (!compare(model, enhancerModel)) {
                  continue;
              }
              try {
                  //增加实验次数
                  boolean pass = limitAndIncrease(statusMetric);
                  if (!pass) {
                      LOGGER.info("Limited by: {}", JSON.toJSONString(model));
                      break;//终止
                  }
                  LOGGER.info("Match rule: {}", JSON.toJSONString(model));
                  enhancerModel.merge(model);
                  ModelSpec modelSpec = ManagerFactory.getModelSpecManager().getModelSpec(target);
                  ActionSpec actionSpec = modelSpec.getActionSpec(model.getActionName());
                  //核心：遍历拿出执行器，执行run方法，无非就是延迟就等待时间，抛异常就抛异常
                  actionSpec.getActionExecutor().run(enhancerModel);
              } catch (InterruptProcessException e) {
                  throw e;
              } catch (UnsupportedReturnTypeException e) {
                  LOGGER.warn("unsupported return type for return experiment", e);
                  // decrease the count if throw unexpected exception
                  statusMetric.decrease();
              } catch (Throwable e) {
                  LOGGER.warn("inject exception", e);
                  // decrease the count if throw unexpected exception
                  statusMetric.decrease();
              }
              // break it if compared success
              break;
          }
      }
  ```
- 参考以及扩展：
1.https://dubbo.apache.org/zh-cn/blog
2.https://github.com/chaosblade-io
3.https://github.com/alibaba/jvm-sandbox
4.https://www.infoq.cn/article/TSY4lGjvSfwEuXEBW*Gp
5.https://www.infoq.cn/article/javaagent-illustrated/
第一次写技术文章输出，同时感谢我大佬指导：@Stool233
