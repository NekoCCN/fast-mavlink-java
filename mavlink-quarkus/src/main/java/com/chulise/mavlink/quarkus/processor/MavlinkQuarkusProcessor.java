package com.chulise.mavlink.quarkus.processor;

import com.chulise.mavlink.quarkus.MavlinkListener;
import com.chulise.mavlink.quarkus.MavlinkSubscribe;
import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Filer;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.MirroredTypeException;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;

@SupportedAnnotationTypes({
        "com.chulise.mavlink.quarkus.MavlinkListener",
        "com.chulise.mavlink.quarkus.MavlinkSubscribe"
})
@SupportedSourceVersion(SourceVersion.RELEASE_17)
public class MavlinkQuarkusProcessor extends AbstractProcessor
{
    private static final String GEN_PKG = "com.chulise.mavlink.quarkus.generated";
    private static final String GEN_CLASS = "MavlinkGeneratedRegistrar";

    private Types types;
    private Elements elements;

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv)
    {
        super.init(processingEnv);
        this.types = processingEnv.getTypeUtils();
        this.elements = processingEnv.getElementUtils();
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv)
    {
        Map<String, ListenerInfo> listeners = new HashMap<>();

        for (Element element : roundEnv.getElementsAnnotatedWith(MavlinkListener.class))
        {
            if (element.getKind() != ElementKind.CLASS)
            {
                continue;
            }
            TypeElement type = (TypeElement) element;
            MavlinkListener listener = type.getAnnotation(MavlinkListener.class);
            String id = listener.value();

            ListenerInfo info = listeners.computeIfAbsent(id, k -> new ListenerInfo(id));
            info.handlerTypes.add(type);
        }

        for (Element element : roundEnv.getElementsAnnotatedWith(MavlinkSubscribe.class))
        {
            if (element.getKind() != ElementKind.METHOD)
            {
                continue;
            }
            ExecutableElement method = (ExecutableElement) element;
            TypeElement owner = (TypeElement) method.getEnclosingElement();
            MavlinkListener listener = owner.getAnnotation(MavlinkListener.class);
            if (listener == null)
            {
                continue;
            }
            String id = listener.value();

            if (!method.getModifiers().contains(Modifier.PUBLIC))
            {
                processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR,
                        "@MavlinkSubscribe method must be public for native mode", method);
                continue;
            }

            ListenerInfo info = listeners.computeIfAbsent(id, k -> new ListenerInfo(id));
            info.methods.add(new MethodInfo(owner, method));
        }

        if (listeners.isEmpty())
        {
            return false;
        }

        try
        {
            generateRegistrar(processingEnv.getFiler(), listeners);
        } catch (IOException e)
        {
            processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR,
                    "Failed to generate registrar: " + e.getMessage());
        }

        return true;
    }

    private void generateRegistrar(Filer filer, Map<String, ListenerInfo> listeners) throws IOException
    {
        JavaFileObject file = filer.createSourceFile(GEN_PKG + "." + GEN_CLASS);
        try (Writer writer = file.openWriter())
        {
            writer.write("package " + GEN_PKG + ";\n\n");
            writer.write("@jakarta.enterprise.context.ApplicationScoped\n");
            writer.write("public class " + GEN_CLASS + " implements com.chulise.mavlink.quarkus.MavlinkRegistrar {\n");

            List<TypeElement> handlerTypes = uniqueHandlers(listeners);
            int index = 0;
            for (TypeElement type : handlerTypes)
            {
                String field = "h" + index++;
                writer.write("  @jakarta.inject.Inject " + type.getQualifiedName() + " " + field + ";\n");
            }

            writer.write("  @Override public void register(com.chulise.mavlink.quarkus.MavlinkRegistrarContext context) {\n");

            index = 0;
            Map<TypeElement, String> handlerFields = new HashMap<>();
            for (TypeElement type : handlerTypes)
            {
                handlerFields.put(type, "h" + index++);
            }

            for (ListenerInfo info : listeners.values())
            {
                writer.write("    com.chulise.mavlink.quarkus.MavlinkDispatcher dispatcher = context.dispatcher(\""
                        + info.id + "\");\n");
                writer.write("    if (dispatcher == null) { throw new IllegalStateException(\"listener id not found: "
                        + info.id + "\"); }\n");

                for (MethodInfo mi : info.methods)
                {
                    writeMethodRegistration(writer, handlerFields.get(mi.owner), mi);
                }
            }

            writer.write("  }\n");
            writer.write("}\n");
        }
    }

    private void writeMethodRegistration(Writer writer, String handlerField, MethodInfo method) throws IOException
    {
        ExecutableElement m = method.method;
        MavlinkSubscribe sub = m.getAnnotation(MavlinkSubscribe.class);

        if (m.getParameters().size() != 1)
        {
            processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR,
                    "@MavlinkSubscribe method must have exactly one parameter", m);
            return;
        }

        TypeMirror paramType = m.getParameters().get(0).asType();
        String paramTypeName = paramType.toString();
        boolean raw = sub.raw() || "com.chulise.mavlink.core.MavlinkPacketView".equals(paramTypeName);

        if (raw)
        {
            writer.write("    dispatcher.registerRaw(arg -> " + handlerField + "." + m.getSimpleName()
                    + "((" + paramTypeName + ")arg));\n");
            return;
        }

        String viewType = resolveViewType(sub, paramTypeName);
        if (!isMavlinkView(viewType))
        {
            processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR,
                    "@MavlinkSubscribe parameter must be a MavlinkView", m);
            return;
        }
        int messageId = sub.messageId();
        String messageExpr = (messageId >= 0) ? String.valueOf(messageId) : (viewType + ".ID");

        writer.write("    dispatcher.addHandler(" + messageExpr + ", " + viewType + ".class, " +
                "java.lang.ThreadLocal.withInitial(" + viewType + "::new), " +
                "arg -> " + handlerField + "." + m.getSimpleName() + "((" + viewType + ")arg));\n");
    }

    private String resolveViewType(MavlinkSubscribe sub, String paramTypeName)
    {
        String declared = getDeclaredTypeName(sub);
        if (!"java.lang.Void".equals(declared))
        {
            return declared;
        }
        return paramTypeName;
    }

    private String getDeclaredTypeName(MavlinkSubscribe sub)
    {
        try
        {
            sub.value();
        } catch (MirroredTypeException e)
        {
            return e.getTypeMirror().toString();
        }
        return "java.lang.Void";
    }

    private boolean isMavlinkView(String typeName)
    {
        TypeElement mavlinkView = elements.getTypeElement("com.chulise.mavlink.core.MavlinkView");
        TypeElement type = elements.getTypeElement(typeName);
        if (mavlinkView == null || type == null)
        {
            return false;
        }
        return types.isAssignable(type.asType(), mavlinkView.asType());
    }

    private List<TypeElement> uniqueHandlers(Map<String, ListenerInfo> listeners)
    {
        Map<String, TypeElement> map = new HashMap<>();
        for (ListenerInfo info : listeners.values())
        {
            for (TypeElement type : info.handlerTypes)
            {
                map.putIfAbsent(type.getQualifiedName().toString(), type);
            }
            for (MethodInfo mi : info.methods)
            {
                map.putIfAbsent(mi.owner.getQualifiedName().toString(), mi.owner);
            }
        }
        return new ArrayList<>(map.values());
    }

    private static final class ListenerInfo
    {
        final String id;
        final List<TypeElement> handlerTypes = new ArrayList<>();
        final List<MethodInfo> methods = new ArrayList<>();

        ListenerInfo(String id)
        {
            this.id = id;
        }
    }

    private static final class MethodInfo
    {
        final TypeElement owner;
        final ExecutableElement method;

        MethodInfo(TypeElement owner, ExecutableElement method)
        {
            this.owner = owner;
            this.method = method;
        }
    }
}
