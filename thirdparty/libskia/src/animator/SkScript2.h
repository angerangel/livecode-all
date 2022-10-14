/*
 * Copyright 2011 Google Inc.
 *
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */

#ifndef SkScript2_DEFINED
#define SkScript2_DEFINED

#include "SkDisplayType.h"
#include "SkOperand2.h"
#include "SkStream.h"
#include "SkTDArray.h"
#include "SkTDArray_Experimental.h"
#include "SkTDict.h"
#include "SkTDStack.h"

typedef SkLongArray(SkString*) SkTDStringArray;

class SkAnimateMaker;
class SkScriptCallBack;

class SkScriptEngine2 {
public:
    enum Error {
        kNoError,
        kArrayIndexOutOfBounds,
        kCouldNotFindReferencedID,
        kFunctionCallFailed,
        kMemberOpFailed,
        kPropertyOpFailed
    };

    enum Attrs {
        kConstant,
        kVariable
    };

    SkScriptEngine2(SkOperand2::OpType returnType);
    ~SkScriptEngine2();
    bool convertTo(SkOperand2::OpType , SkScriptValue2* );
    bool evaluateScript(const char** script, SkScriptValue2* value);
    void forget(SkOpArray* array);
    Error getError() { return fError; }
    SkOperand2::OpType getReturnType() { return fReturnType; }
    void track(SkOpArray* array) {
        SkASSERT(fTrackArray.find(array) < 0);
        *fTrackArray.append() = array; }
    void track(SkString* string) {
        SkASSERT(fTrackString.find(string) < 0);
        *fTrackString.append() = string;
    }
    static bool ConvertTo(SkScriptEngine2* , SkOperand2::OpType toType, SkScriptValue2* value);
    static SkScalar IntToScalar(int32_t );
    static bool ValueToString(const SkScriptValue2& value, SkString* string);

    enum Op {        // used by tokenizer attribute table
        kUnassigned,
        kAdd,
        kBitAnd,
        kBitNot,
        kBitOr,
        kDivide,
        kEqual,
        kFlipOps,
        kGreaterEqual,
        kLogicalAnd,
        kLogicalNot,
        kLogicalOr,
        kMinus,
        kModulo,
        kMultiply,
        kShiftLeft,
        kShiftRight,    // signed
        kSubtract,
        kXor,
// following not in attribute table
        kArrayOp,
        kElse,
        kIf,
        kParen,
        kLastLogicalOp,
        kArtificialOp = 0x20
    };

    enum TypeOp {    // generated by tokenizer
        kNop, // should never get generated
        kAccumulatorPop,
        kAccumulatorPush,
        kAddInt,
        kAddScalar,
        kAddString,    // string concat
        kArrayIndex,
        kArrayParam,
        kArrayToken,
        kBitAndInt,
        kBitNotInt,
        kBitOrInt,
        kBoxToken,
        kCallback,
        kDivideInt,
        kDivideScalar,
        kDotOperator,
        kElseOp,
        kEnd,
        kEqualInt,
        kEqualScalar,
        kEqualString,
        kFunctionCall,
        kFlipOpsOp,
        kFunctionToken,
        kGreaterEqualInt,
        kGreaterEqualScalar,
        kGreaterEqualString,
        kIfOp,
        kIntToScalar,
        kIntToScalar2,
        kIntToString,
        kIntToString2,
        kIntegerAccumulator,
        kIntegerOperand,
        kLogicalAndInt,
        kLogicalNotInt,
        kLogicalOrInt,
        kMemberOp,
        kMinusInt,
        kMinusScalar,
        kModuloInt,
        kModuloScalar,
        kMultiplyInt,
        kMultiplyScalar,
        kPropertyOp,
        kScalarAccumulator,
        kScalarOperand,
        kScalarToInt,
        kScalarToInt2,
        kScalarToString,
        kScalarToString2,
        kShiftLeftInt,
        kShiftRightInt,    // signed
        kStringAccumulator,
        kStringOperand,
        kStringToInt,
        kStringToScalar,
        kStringToScalar2,
        kStringTrack,
        kSubtractInt,
        kSubtractScalar,
        kToBool,
        kUnboxToken,
        kUnboxToken2,
        kXorInt,
        kLastTypeOp
    };

    enum OpBias {
        kNoBias,
        kTowardsNumber = 0,
        kTowardsString
    };

protected:

    enum BraceStyle {
    //    kStructBrace,
        kArrayBrace,
        kFunctionBrace
    };

    enum AddTokenRegister {
        kAccumulator,
        kOperand
    };

    enum ResultIsBoolean {
        kResultIsNotBoolean,
        kResultIsBoolean
    };

    struct OperatorAttributes {
        unsigned int fLeftType : 3;    // SkOpType union, but only lower values
        unsigned int fRightType : 3;     // SkOpType union, but only lower values
        OpBias fBias : 1;
        ResultIsBoolean fResultIsBoolean : 1;
    };

    struct Branch {
        Branch() {
        }

        Branch(Op op, int depth, size_t offset)
            : fOffset(SkToU16(offset)), fOpStackDepth(depth), fOperator(op)
            , fPrimed(kIsNotPrimed), fDone(kIsNotDone) {
        }

        enum Primed {
            kIsNotPrimed,
            kIsPrimed
        };

        enum Done {
            kIsNotDone,
            kIsDone,
        };

        unsigned fOffset : 16; // offset in generated stream where branch needs to go
        int fOpStackDepth : 7; // depth when operator was found
        Op fOperator : 6; // operand which generated branch
        mutable Primed fPrimed : 1;    // mark when next instruction generates branch
        Done fDone : 1;    // mark when branch is complete
        void prime() { fPrimed = kIsPrimed; }
        void resolve(SkDynamicMemoryWStream* , size_t offset);
    };

    static const OperatorAttributes gOpAttributes[];
    static const signed char gPrecedence[];
    static const TypeOp gTokens[];
    void addToken(TypeOp );
    void addTokenConst(SkScriptValue2* , AddTokenRegister , SkOperand2::OpType , TypeOp );
    void addTokenInt(int );
    void addTokenScalar(SkScalar );
    void addTokenString(const SkString& );
    void addTokenValue(const SkScriptValue2& , AddTokenRegister );
    int arithmeticOp(char ch, char nextChar, bool lastPush);
    bool convertParams(SkTDArray<SkScriptValue2>* ,
        const SkOperand2::OpType* paramTypes, int paramTypeCount);
    void convertToString(SkOperand2* operand, SkOperand2::OpType type) {
        SkScriptValue2 scriptValue;
        scriptValue.fOperand = *operand;
        scriptValue.fType = type;
        convertTo(SkOperand2::kString, &scriptValue);
        *operand = scriptValue.fOperand;
    }
    bool evaluateDot(const char*& script);
    bool evaluateDotParam(const char*& script, const char* field, size_t fieldLength);
    bool functionParams(const char** scriptPtr, SkTDArray<SkScriptValue2>* params);
    size_t getTokenOffset();
    SkOperand2::OpType getUnboxType(SkOperand2 scriptValue);
    bool handleArrayIndexer(const char** scriptPtr);
    bool handleFunction(const char** scriptPtr);
    bool handleMember(const char* field, size_t len, void* object);
    bool handleMemberFunction(const char* field, size_t len, void* object,
        SkTDArray<SkScriptValue2>* params);
    bool handleProperty();
    bool handleUnbox(SkScriptValue2* scriptValue);
    bool innerScript(const char** scriptPtr, SkScriptValue2* value);
    int logicalOp(char ch, char nextChar);
    void processLogicalOp(Op op);
    bool processOp();
    void resolveBranch(Branch& );
//    void setAnimateMaker(SkAnimateMaker* maker) { fMaker = maker; }
    SkDynamicMemoryWStream fStream;
    SkDynamicMemoryWStream* fActiveStream;
    SkTDStack<BraceStyle> fBraceStack;        // curly, square, function paren
    SkTDStack<Branch> fBranchStack;  // logical operators, slot to store forward branch
    SkLongArray(SkScriptCallBack*) fCallBackArray;
    SkTDStack<Op> fOpStack;
    SkTDStack<SkScriptValue2> fValueStack;
//    SkAnimateMaker* fMaker;
    SkLongArray(SkOpArray*) fTrackArray;
    SkTDStringArray fTrackString;
    const char* fToken; // one-deep stack
    size_t fTokenLength;
    SkOperand2::OpType fReturnType;
    Error fError;
    SkOperand2::OpType fAccumulatorType;    // tracking for code generation
    SkBool fBranchPopAllowed;
    SkBool fConstExpression;
    SkBool fOperandInUse;
private:
#ifdef SK_DEBUG
public:
    void decompile(const unsigned char* , size_t );
    static void UnitTest();
    static void ValidateDecompileTable();
#endif
};

#ifdef SK_DEBUG

struct SkScriptNAnswer2 {
    const char* fScript;
    SkOperand2::OpType fType;
    int32_t fIntAnswer;
    SkScalar fScalarAnswer;
    const char* fStringAnswer;
};

#endif


#endif // SkScript2_DEFINED
