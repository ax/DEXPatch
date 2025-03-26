//
// DEXPatch.java v0.1
// Surgically inject a System.loadLibrary() into a dex.
// author: ax - github.com/ax
//
// -----------------------------------------------------------------------------
// The following program can be used to inject a System.loadLibrary() call into
// the <clinit> of the specified class in a COMPILED dex.
// Thanks to dexlib2, that performs direct bytecode manipulation, this avoids
// decompilation/recompilation errors and preserves original obfuscation and optimizations.
//
// Here is used to inject a System.loadLibrary("frida-gadget") call in a suitable
// place that typically is the static initializer the main application Activity.
// 
// EXAMPLE USAGE:
// java -cp .:dexlib2-2.5.2.jar:guava-33.0.0-jre.jar DEXPatch input.dex output.dex com/ax/example/MainActivity
// java -jar DEXPatch.jar input.dex output.dex com/ax/example/MainActivity
// -----------------------------------------------------------------------------

import com.google.common.collect.Lists; // dexlib2 has a dependency on Guava
import org.jf.dexlib2.*;
import org.jf.dexlib2.builder.MutableMethodImplementation;
import org.jf.dexlib2.builder.instruction.*;
import org.jf.dexlib2.iface.*;
import org.jf.dexlib2.iface.instruction.Instruction;
import org.jf.dexlib2.iface.instruction.formats.*;
import org.jf.dexlib2.immutable.ImmutableClassDef;
import org.jf.dexlib2.immutable.ImmutableMethod;
import org.jf.dexlib2.immutable.reference.ImmutableMethodReference;
import org.jf.dexlib2.immutable.reference.ImmutableStringReference;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class DEXPatch {
    private static final String FRIDA_LIB_NAME = "frida-gadget";
    private static final Map<String, Integer> DEX_VERSION_TO_API = Map.of(
        "035", 24,  // Android 7.0
        "037", 25,  // Android 7.1
        "038", 26,  // Android 8.0
        "039", 28,  // Android 9.0
        "040", 30   // Android 11
    );

     public static void main(String[] args) throws IOException {
        System.out.println("[*] DEXPatch v0.1");
        if (args.length != 3) {
            System.out.println("Usage:  java -jar DEXPatch.jar <input_dex> <output_dex> <target_class>");
            System.out.println("        java -jar DEXPatch.jar input.dex output.dex com/ax/example/MainActivity");
            System.exit(1);
        }
        String inputDex = args[0];
        String outputDex = args[1];
        String targetClassArg = args[2];
        String targetClass = "L"+targetClassArg+";";
        injectFridaGadget(inputDex, outputDex, targetClass);
        System.out.println("[>] Successfully injected Frida gadget, output dex is: " + outputDex);
    }

    public static void injectFridaGadget(String inputDexPath, String outputDexPath, String targetClass) throws IOException {
        File inputFile = new File(inputDexPath);
        // Detect DEX version from file header
        String dexVersion = getDexVersion(inputFile);
        int apiLevel = mapDexVersionToApi(dexVersion);
        Opcodes opcodes = Opcodes.forApi(apiLevel);
        // Load dex file with correct version
        DexFile dexFile = DexFileFactory.loadDexFile(inputFile, opcodes);
        Set<ClassDef> modifiedClasses = new HashSet<>();
        for (ClassDef classDef : dexFile.getClasses()) {
            modifiedClasses.add(classDef.getType().equals(targetClass)
                ? processClass(classDef, targetClass)
                : classDef);
        }
        // Write with original DEX version
        DexFileFactory.writeDexFile(outputDexPath, new DexFile() {
            @Override public Set<? extends ClassDef> getClasses() { return modifiedClasses; }
            @Override public Opcodes getOpcodes() { return opcodes; }
        });
        System.out.println("[>] Done!");
    }

    // Read DEX version from file header
    private static String getDexVersion(File dexFile) throws IOException {
        try (RandomAccessFile raf = new RandomAccessFile(dexFile, "r")) {
            byte[] magic = new byte[8];
            raf.readFully(magic);
            String version = new String(magic, 4, 4, StandardCharsets.US_ASCII)
                .replaceAll("[^0-9]", "");
            if (version.isEmpty()) throw new IOException("[!] Invalid DEX format");
            return version;
        }
    }

    private static int mapDexVersionToApi(String dexVersion) {
        Integer apiLevel = DEX_VERSION_TO_API.get(dexVersion);
        if (apiLevel == null) {
            throw new IllegalArgumentException("[!] Unsupported DEX version: " + dexVersion);
        }
        return apiLevel;
    }

    private static ClassDef processClass(ClassDef originalClass, String targetClass) {
        List<Method> methods = new ArrayList<>();
        Method existingClinit = null;
        // Find existing <clinit>
        for (Method method : originalClass.getMethods()) {
            if (method.getName().equals("<clinit>")) {
                existingClinit = method;
                System.out.println("[>] <clinit> found!");
            } else {
                methods.add(method);
            }
        }
        // Create new <clinit>
        methods.add(createModifiedClinit(existingClinit, targetClass));
        return new ImmutableClassDef(
            originalClass.getType(),
            originalClass.getAccessFlags(),
            originalClass.getSuperclass(),
            originalClass.getInterfaces(),
            originalClass.getSourceFile(),
            originalClass.getAnnotations(),
            originalClass.getFields(),
            methods
        );
    }

    private static Method createModifiedClinit(Method existingClinit, String targetClass) {
        MutableMethodImplementation impl = new MutableMethodImplementation(2);
        // Add Frida load instructions
        addFridaLoadInstructions(impl);
        // Copy original instructions (if any)
        if (existingClinit != null && existingClinit.getImplementation() != null) {
            System.out.println("[>] Copying original <clinit> instructions....");
            for (Instruction instruction : existingClinit.getImplementation().getInstructions()) {
                if (instruction.getOpcode() != Opcode.RETURN_VOID) {
                    addAsBuilderInstruction(impl, instruction);
                }
            }
        }
        impl.addInstruction(new BuilderInstruction10x(Opcode.RETURN_VOID));
        return new ImmutableMethod(
            targetClass,
            "<clinit>",
            Collections.emptyList(),
            "V",
            AccessFlags.STATIC.getValue() | AccessFlags.CONSTRUCTOR.getValue(),
            Collections.emptySet(),
            null,
            impl
        );
    }

    private static void addAsBuilderInstruction(MutableMethodImplementation impl, Instruction instruction) {
    try {
        if (instruction instanceof Instruction10x) {
            impl.addInstruction(new BuilderInstruction10x(instruction.getOpcode()));
        } else if (instruction instanceof Instruction11n) {
            Instruction11n i = (Instruction11n) instruction;
            impl.addInstruction(new BuilderInstruction11n(i.getOpcode(), i.getRegisterA(), i.getNarrowLiteral()));
        } else if (instruction instanceof Instruction21c) {
            Instruction21c i = (Instruction21c) instruction;
            impl.addInstruction(new BuilderInstruction21c(i.getOpcode(), i.getRegisterA(), i.getReference()));
        } else if (instruction instanceof Instruction35c) {
            Instruction35c i = (Instruction35c) instruction;
            impl.addInstruction(new BuilderInstruction35c(
                i.getOpcode(),
                i.getRegisterCount(),
                i.getRegisterC(),
                i.getRegisterD(),
                i.getRegisterE(),
                i.getRegisterF(),
                i.getRegisterG(),
                i.getReference()
            ));
        } else if (instruction instanceof Instruction21s) {
            Instruction21s i = (Instruction21s) instruction;
            impl.addInstruction(new BuilderInstruction21s(
                i.getOpcode(),
                i.getRegisterA(),
                i.getNarrowLiteral()
            ));
        } else if (instruction instanceof Instruction31i) {
            Instruction31i i = (Instruction31i) instruction;
            impl.addInstruction(new BuilderInstruction31i(
                i.getOpcode(),
                i.getRegisterA(),
                i.getNarrowLiteral()
            ));
        } else {
            System.err.println("[!] Warning: Skipping unhandled instruction type: " +
                instruction.getClass().getSimpleName() + " (" + instruction.getOpcode() + ")");
        }
    } catch (Exception e) {
        throw new RuntimeException("[!] Error converting instruction: " + instruction.getOpcode(), e);
    }
    }

    private static void addFridaLoadInstructions(MutableMethodImplementation impl) {
        System.out.println("[>] Adding System.loadLibrary(\"frida-gadget\") call....");
        // const-string v0, "frida-gadget"
        impl.addInstruction(new BuilderInstruction21c(
            Opcode.CONST_STRING,
            0,
            new ImmutableStringReference(FRIDA_LIB_NAME)
        ));
        // invoke-static {v0}, Ljava/lang/System;->loadLibrary(Ljava/lang/String;)V
        impl.addInstruction(new BuilderInstruction35c(
            Opcode.INVOKE_STATIC,
            1,  // 1 argument
            0, 0, 0, 0, 0,  // Registers (only v0)
            new ImmutableMethodReference(
                "Ljava/lang/System;",
                "loadLibrary",
                Lists.newArrayList("Ljava/lang/String;"),
                "V"
            )
        ));
    }
}
