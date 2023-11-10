## 插件能力
1. 分析模块之间的引用关系
2. 依赖重复类检查
3. 未解决的引用检查，避免运行时出现 `NoClassDefFoundError`、`NoSuchMethodError`、`NoSuchFieldError` 等异常


## 插件介绍
通过字节码来分析依赖之间的真实引用关系,分析效果如下，具体可查看 [moduleRef.json](./moduleRef.json) 文件

```json
{
  "androidx.compose.ui:ui:1.3.0": {
    "dependencies": [
      "org.jetbrains.kotlin:kotlin-stdlib:1.7.20",
      "androidx.compose.ui:ui-unit:1.3.0",
      "androidx.compose.runtime:runtime:1.3.0",
      "androidx.compose.ui:ui-graphics:1.3.0",
      "//............."
    ],
    "unsolved": {
      "clazz": [
        "android.view.RenderNode",
        "android.view.DisplayListCanvas"
      ],
      "fields": [
        "androidx.compose.ui.platform.RenderNodeApi23_android.view.RenderNode"
      ],
      "methods": []
    }
  }
}
```
- `dependencies` 为 `androidx.compose.ui:ui:1.3.0` 所使用到的依赖
- `unsolved` 为 `androidx.compose.ui:ui:1.3.0` 依赖使用到的 类、字段和方法在整个依赖关系中都找不到


其他说明：
- 有的模块可能就是会报 unsolved，例如 androidx.compose.ui:ui 依赖的 RenderNodeApi23 与 RenderNodeApi29 类中的 RenderNode，他们的包名在不同的 SDK 版本不一样，但他们在运行阶段会通过 SDK 版本来选择加载哪个类，所以，类似这类的 unsolved 是可以放过的，但前提是做好 review

## 生成依赖引用关系图
插件还会根据 moduleRef.json 生成 [moduleRef.puml](./moduleRef.puml) 文件，如果 AS 安装了 plantUML 插件的话，可以直接在 AS 中查看依赖关系图，如果没装的话，也可以导入语雀查看，如下是通过语雀导出的 [moduleRef.svg](./moduleRef.svg) 图

## todo
1、AbstractMethodError 异常检查：检查是否存在父类(抽象方法、接口方法)方法被子类覆盖，如果父类有，但子类没有，则说明会发生该异常