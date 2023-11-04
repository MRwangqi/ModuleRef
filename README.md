# ModuleRef



白名单检测的一些说明：
- 有的模块可能就是会报 unsolved，例如 androidx.compose.ui:ui 依赖的 RenderNodeApi23 与 RenderNodeApi29 类中的 RenderNode，他们的包名在不同的 SDK 版本不一样，但他们在运行阶段会通过 SDK 版本来选择加载哪个类，所以，类似这类的 unsolved 是可以放过的，但前提是做好 review
