# Reified
Reified in Java 11 and upwards

### What is Reified
Reified is an Annotation for Java 11 and upwards inspired by the reified keyword in Kotlin.
Kotlin's approach requires the instruction to also be inlined, excluding as a result classes by design.
This library supports also classes instead as inlining is not needed.
Reified creates a local variable, or a parameter of type Class<T> for each type parameter annotated.
The correct type is deduced and passed to the tree that owns the type variable 
based on the enclosing statements(ex. return statements and variable declarations) and the type parameters of the invocation.
If a non annotated type parameter is needed to determine the type of annotated one, Reified will take care of automatically applying
the same process to said parameters to determine the first.

### How to install

#### Maven
```xml
<dependencies>
    <dependency>
        <groupId>com.github.auties00</groupId>
        <artifactId>reified</artifactId>
        <version>2.0</version>
    </dependency>
</dependencies>

<build>
    <plugins>
        <plugin>
            <groupId>org.apache.maven.plugins</groupId>
            <artifactId>maven-compiler-plugin</artifactId>
            <version>3.8.1</version>
            <configuration>
                <annotationProcessorPaths>
                    <path>
                        <groupId>com.github.auties00</groupId>
                        <artifactId>reified</artifactId>
                        <version>2.0</version>
                    </path>
                </annotationProcessorPaths>
            </configuration>
        </plugin>
    </plugins>
</build>
```

#### Gradle
```groovy
implementation 'com.github.auties00:reified:2.0'
annotationProcessor 'com.github.auties00:reified:2.0'
```

#### Plugins
In order to make linting work in your favourite IDE, a plugin is needed. 

Most common IDEs:
1. IntelliJ - [IntelliJ Marketplace](https://plugins.jetbrains.com/plugin/17786-reified), [Source Code](https://github.com/Auties00/ReifiedIdeaPlugin)
2. Eclipse - Not yet
3. NetBeans - Not yet

### Example
With reified:
```java
class JsonUtils {
    private static final ObjectMapper JACKSON = new ObjectMapper();

    public static <@Reified T> T fromJson(String json){
        try {
            return JACKSON.readValue(json, T);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }
}

record ExampleObject(String name) {
    public static ExampleObject fromJson(String json){
        return JsonUtils.fromJson(json);
    }
}
```

Without reified:

```java
import java.io.IOException;
import java.io.UncheckedIOException;

class JsonUtils {
    private static final ObjectMapper JACKSON = new ObjectMapper();

    public static <T> T fromJson(String json, Class<T> clazz) {
        try {
            return JACKSON.readValue(json, clazz);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }
}

record ExampleObject(String name) {
    public static ExampleObject fromJson(String json) {
        return JsonUtils.fromJson(json, ExampleObject.class);
    }
}
```
