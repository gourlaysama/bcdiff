package org.bcdiff

import org.objectweb.asm.Opcodes._
import org.objectweb.asm.tree._
import org.objectweb.asm.{Label, Handle}
import java.util.{List => JList}

object ByteCode {

  val method_access_flags = Map(
    ACC_PUBLIC -> "PUBLIC",
    ACC_PRIVATE -> "PRIVATE",
    ACC_PROTECTED -> "PROTECTED",
    ACC_STATIC -> "STATIC",
    ACC_FINAL -> "FINAL",
    ACC_SYNCHRONIZED -> "SYNCHRONIZED",
    ACC_BRIDGE -> "BRIDGE",
    ACC_VARARGS -> "VARARGS",
    ACC_NATIVE -> "NATIVE",
    ACC_ABSTRACT -> "ABSTRACT",
    ACC_STRICT -> "STRICT",
    ACC_SYNTHETIC -> "SYNTHETIC"
  )

  val class_access_flags = Map(
    ACC_PUBLIC -> "PUBLIC",
    ACC_PRIVATE -> "PRIVATE",
    ACC_PROTECTED -> "PROTECTED",
    ACC_FINAL -> "FINAL",
    ACC_SUPER -> "SUPER",
    ACC_INTERFACE -> "INTERFACE",
    ACC_ABSTRACT -> "ABSTRACT",
    ACC_SYNTHETIC -> "SYNTHETIC",
    ACC_ANNOTATION -> "ANNOTATION",
    ACC_ENUM -> "ENUM"
  )

  val field_access_flags = Map(
    ACC_PUBLIC -> "PUBLIC",
    ACC_PRIVATE -> "PRIVATE",
    ACC_PROTECTED -> "PROTECTED",
    ACC_STATIC -> "STATIC",
    ACC_FINAL -> "FINAL",
    ACC_VOLATILE -> "VOLATILE",
    ACC_TRANSIENT -> "TRANSIENT",
    ACC_SYNTHETIC -> "SYNTHETIC",
    ACC_ENUM -> "ENUM"
  )

  def convert(a: AbstractInsnNode): ByteCode = {
    import scala.collection.JavaConversions._
    a.getOpcode match {
      case op@(BIPUSH | SIPUSH | NEWARRAY) => IntOp(op, a.asInstanceOf[IntInsnNode].operand)
      case LDC => LoadOp(a.asInstanceOf[LdcInsnNode].cst)
      case op@(ILOAD | FLOAD | DLOAD | ALOAD | ISTORE | LSTORE | FSTORE | DSTORE | ASTORE) =>
        VarOp(op, a.asInstanceOf[VarInsnNode].`var`)
      case IINC =>
        val b = a.asInstanceOf[IincInsnNode]
        IincOp(b.`var`, b.incr)
      case op@(IFEQ | IFNE | IFLT | IFGE | IFGT | IFLE | IF_ICMPEQ | IF_ICMPNE | IF_ICMPLT | IF_ICMPGE | IF_ICMPGT |
               IF_ICMPLE | IF_ACMPEQ | IF_ACMPNE | GOTO | JSR | RET | IFNULL | IFNONNULL) =>
        JumpOp(op, a.asInstanceOf[JumpInsnNode].label.getLabel)
      case TABLESWITCH =>
        val b = a.asInstanceOf[TableSwitchInsnNode]
        TableSwitchOp(b.min, b.max, b.dflt.getLabel, b.labels.asInstanceOf[JList[LabelNode]].map(_.getLabel))
      case LOOKUPSWITCH =>
        val b = a.asInstanceOf[LookupSwitchInsnNode]
        LookupSwitchOp(b.dflt.getLabel, b.keys.asInstanceOf[JList[Int]], b.labels.asInstanceOf[JList[LabelNode]].map(_.getLabel))
      case op@(GETSTATIC | PUTSTATIC | GETFIELD | PUTFIELD) =>
        val b = a.asInstanceOf[FieldInsnNode]
        FieldOp(op, b.name, b.owner, b.desc)
      case op@(INVOKEVIRTUAL | INVOKESPECIAL | INVOKESTATIC | INVOKEINTERFACE) =>
        val b = a.asInstanceOf[MethodInsnNode]
        MethodOp(op, b.name, b.owner, b.desc)
      case INVOKEDYNAMIC =>
        val b = a.asInstanceOf[InvokeDynamicInsnNode]
        InvokeDynOp(b.name, b.desc, b.bsm, b.bsmArgs)
      case op@(NEW | NEWARRAY | ANEWARRAY | CHECKCAST | INSTANCEOF) => TypeOp(op, a.asInstanceOf[TypeInsnNode].desc)
      case MULTIANEWARRAY =>
        val b = a.asInstanceOf[MultiANewArrayInsnNode]
        MultiArrayOp(b.desc, b.dims)
      case -1 => a.getType match {
        case AbstractInsnNode.LABEL => LabelOp(a.asInstanceOf[LabelNode].getLabel)
        case AbstractInsnNode.LINE => sys.error("Line number found")
        case AbstractInsnNode.FRAME => sys.error("Frame found")
      }
      case rest => ZeroOp(rest)
    }
  }
}

sealed trait ByteCode {
  def opCode: Int
}

case class ZeroOp(opCode: Int) extends ByteCode {
  override def toString = {
    opCode match {
      case NOP => "nop"
      case ACONST_NULL => "aconst_null"
      case ICONST_M1 => "iconst_m1"
      case ICONST_0 => "iconst_0"
      case ICONST_1 => "iconst_1"
      case ICONST_2 => "iconst_2"
      case ICONST_3 => "iconst_3"
      case ICONST_4 => "iconst_4"
      case ICONST_5 => "iconst_5"
      case LCONST_0 => "lconst_0"
      case LCONST_1 => "lconst_1"
      case FCONST_0 => "fconst_0"
      case FCONST_1 => "fconst_1"
      case FCONST_2 => "fconst_2"
      case DCONST_0 => "dconst_0"
      case DCONST_1 => "dconst_1"
      case IALOAD => "iaload"
      case LALOAD => "laload"
      case FALOAD => "faload"
      case DALOAD => "daload"
      case AALOAD => "aaload"
      case BALOAD => "baload"
      case CALOAD => "caload"
      case SALOAD => "saload"
      case IASTORE => "iastore"
      case LASTORE => "lastore"
      case FASTORE => "fastore"
      case DASTORE => "dastore"
      case AASTORE => "aastore"
      case BASTORE => "bastore"
      case CASTORE => "castore"
      case SASTORE => "sastore"
      case POP => "pop"
      case POP2 => "pop2"
      case DUP => "dup"
      case DUP_X1 => "dup_x1"
      case DUP_X2 => "dup_x2"
      case DUP2 => "dup2"
      case DUP2_X1 => "dup2_x1"
      case DUP2_X2 => "dup2_x2"
      case SWAP => "swap"
      case IADD => "iadd"
      case LADD => "ladd"
      case FADD => "fadd"
      case DADD => "dadd"
      case ISUB => "isub"
      case LSUB => "lsub"
      case FSUB => "fsub"
      case DSUB => "dsub"
      case IMUL => "imul"
      case LMUL => "lmul"
      case FMUL => "fmul"
      case DMUL => "dmul"
      case IDIV => "idiv"
      case LDIV => "ldiv"
      case FDIV => "fdiv"
      case DDIV => "ddiv"
      case IREM => "irem"
      case LREM => "lrem"
      case FREM => "frem"
      case DREM => "drem"
      case INEG => "ineg"
      case LNEG => "lneg"
      case FNEG => "fneg"
      case DNEG => "dneg"
      case ISHL => "ishl"
      case LSHL => "lshl"
      case ISHR => "ishl"
      case LSHR => "lshr"
      case IUSHR => "iushr"
      case LUSHR => "lushr"
      case IAND => "iand"
      case LAND => "land"
      case IOR => "ior"
      case LOR => "lor"
      case IXOR => "ixor"
      case LXOR => "lxor"
      case I2L => "i2l"
      case I2F => "i2f"
      case I2D => "i2d"
      case L2I => "l2i"
      case L2F => "l2f"
      case L2D => "l2d"
      case F2I => "f2i"
      case F2L => "f2l"
      case F2D => "f2d"
      case D2I => "d2i"
      case D2L => "d2l"
      case D2F => "d2f"
      case I2B => "i2b"
      case I2C => "i2c"
      case I2S => "i2s"
      case LCMP => "lcmp"
      case FCMPL => "fcmpl"
      case FCMPG => "fcmpg"
      case DCMPL => "dcmpl"
      case DCMPG => "dcmpg"
      case IRETURN => "ireturn"
      case LRETURN => "lreturn"
      case FRETURN => "freturn"
      case DRETURN => "dreturn"
      case ARETURN => "areturn"
      case RETURN => "return"
      case ARRAYLENGTH => "arraylength"
      case ATHROW => "athrow"
      case MONITORENTER => "monitorenter"
      case MONITOREXIT => "monitorexit"
    }
  }
}

case class IntOp(opCode: Int, operand: Int) extends ByteCode {
  override def toString = {
    (opCode match {
      case BIPUSH => "bipush "
      case SIPUSH => "sipush"
      case NEWARRAY => "newarray "
    }) + operand
  }
}

case class VarOp(opCode: Int, index: Int) extends ByteCode {
  override def toString = {
    (opCode match {
      case ILOAD => "iload "
      case LLOAD => "lload "
      case FLOAD => "fload "
      case DLOAD => "dload "
      case ALOAD => "aload "
      case ISTORE => "istore "
      case LSTORE => "lstore "
      case FSTORE => "fstore "
      case DSTORE => "dstore "
      case ASTORE => "astore "
      case RET => "ret "
    }) + index
  }
}

case class TypeOp(opCode: Int, typeName: String) extends ByteCode {
  override def toString = {
    (opCode match {
      case NEW => "new "
      case ANEWARRAY => "anewarray "
      case CHECKCAST => "checkcast "
      case INSTANCEOF => "instanceof "
    }) + typeName
  }
}

case class FieldOp(opCode: Int, name: String, owner: String, desc: String) extends ByteCode {
  override def toString = {
    val op = (opCode match {
      case GETSTATIC => "getstatic "
      case PUTSTATIC => "putstatic "
      case GETFIELD => "getfield "
      case PUTFIELD => "putfield "
    })

    s"$op // Field $owner.$name:$desc"
  }
}

case class MethodOp(opCode: Int, name: String, owner: String, desc: String) extends ByteCode {
  override def toString = {
    val op = (opCode match {
      case INVOKEVIRTUAL => "invokevirtual "
      case INVOKESPECIAL => "invokespecial "
      case INVOKESTATIC => "invokestatic "
      case INVOKEINTERFACE => "invokeinterface "
    })

    s"$op // Method $owner.$name:$desc"
  }
}

case class InvokeDynOp(name: String, desc: String, mHandle: Handle, params: Seq[AnyRef]) extends ByteCode {

  val opCode: Int = INVOKEDYNAMIC

  override def toString = {
    s"invokedynamic // $name:$desc  $mHandle with (${params.mkString(", ")})"
  }
}

case class JumpOp(opCode: Int, label: Label) extends ByteCode {
  override def toString = {
    opCode match {
      case IFEQ => "ifeq "
      case IFNE => "ifne "
      case IFGE => "ifge "
      case IFGT => "ifgt "
      case IFLE => "ifle "
      case IF_ICMPEQ => "if_icmpeq "
      case IF_ICMPNE => "if_icmpne "
      case IF_ICMPLT => "if_icmplt "
      case IF_ICMPGE => "if_icmpge "
      case IF_ICMPGT => "if_icmpgt "
      case IF_ICMPLE => "if_icmple "
      case IF_ACMPEQ => "if_acmpeq "
      case IF_ACMPNE => "if_acmpne "
      case GOTO => "goto "
      case JSR => "jsr "
      case IFNULL => "ifnull "
      case IFNONNULL => "ifnonnull "
    }
  }
}

case class LabelOp(label: Label) extends ByteCode {
  val opCode = -1

  override def equals(x: Any) = x.isInstanceOf[LabelOp]

  override def toString = "label " + label
}

case class LoadOp(arg: AnyRef) extends ByteCode {
  val opCode = LDC

  override def toString = "ldc // " + arg.getClass.getSimpleName + " " + arg.toString
}

case class IincOp(variable: Int, increment: Int) extends ByteCode {
  val opCode = IINC

  override def toString = s"iinc $variable, $increment"
}

case class TableSwitchOp(min: Int, max: Int, default: Label, labels: Seq[Label]) extends ByteCode {
  val opCode = TABLESWITCH

  override def toString = s"tableswitch $min to $max, default: $default, ${labels.size} labels"
}

case class LookupSwitchOp(default: Label, keys: Seq[Int], labels: Seq[Label]) extends ByteCode {
  val opCode = TABLESWITCH

  override def toString = s"lookupswitch default: $default, ${labels.size} labels"
}

case class MultiArrayOp(desc: String, dim: Int) extends ByteCode {
  val opCode = MULTIANEWARRAY

  override def toString = s"multianewarray $desc, $dim"
}