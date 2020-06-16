package rhino.classfile;

final class ExceptionTableEntry{
    ExceptionTableEntry(int startLabel, int endLabel,
                        int handlerLabel, short catchType){
        itsStartLabel = startLabel;
        itsEndLabel = endLabel;
        itsHandlerLabel = handlerLabel;
        itsCatchType = catchType;
    }

    int itsStartLabel;
    int itsEndLabel;
    int itsHandlerLabel;
    short itsCatchType;
}
