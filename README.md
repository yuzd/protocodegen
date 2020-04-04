### proto3代码生成器

支持jetbrains 旗下所有的ide

例如：
- idea 生成java代码
- rider 生成csharp代码
- 等等你自己探索吧

### 官方下载地址：https://plugins.jetbrains.com/plugin/14067

可以Plugins里面搜索：protocodegen 进行安装

### 安装插件后如何使用

### 1. 你需要把你的proto文件放在一个文件夹，而且需要这个文件夹名称包含 proto 关键字

例如下图： 我把希望生成代码的proto文件和它所引用到的 统统都放在了 protofiles文件夹

![image](https://images4.c-ctrip.com/target/zb041f000001fxw7hC7DE.png)


### 2. 你需要在这个文件夹下面放一个 proto.properties文件
配置内容如下：


Key | Value
---|---
outFolder | 代码生成的存放目录， 例如：../src/java （相对于文件夹的相对路径）
lang | 默认： java

![image](https://images4.c-ctrip.com/target/zb071f000001gflnf791A.png)


### 3. 在文件夹点击右键 你会发现 一个 ProtoCodeGen 功能键


![image](https://images4.c-ctrip.com/target/zb0p1f000001fy1qn149A.png)

### enjoy
