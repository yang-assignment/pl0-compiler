import java.util.BitSet;

/**
 *　　语法分析器。这是PL/0分析器中最重要的部分，在语法分析的过程中嵌入了语法错误检查和目标代码生成。
 */
public class Parser {
	private Scanner lex;					// 对词法分析器的引用
	private Table table;					// 对符号表的引用
	private Interpreter interp;				// 对目标代码生成器的引用
	
	// 表示声明开始的符号集合、表示语句开始的符号集合、表示因子开始的符号集合
	// 实际上这就是声明、语句和因子的FIRST集合
	private BitSet declbegsys, statbegsys, facbegsys;
	
	/**
	 * 当前符号的符号码，由nextsym()读入
	 * @see #nextsym()
	 */
	private int symtype;
	
	/**
	 * 当前符号，由nextsym()读入
	 * @see #nextsym()
	 */
	private Symbol sym;
	
	/**
	 * 当前作用域的堆栈帧大小，或者说数据大小（data size）
	 */
	private int dx = 0;
	
	/**
	 * 构造并初始化语法分析器，这里包含了C语言版本中init()函数的一部分代码
	 * @param l 编译器的词法分析器
	 * @param t 编译器的符号表
	 * @param i 编译器的目标代码生成器
	 */
	public Parser(Scanner l, Table t, Interpreter i) {
		lex = l;
		table = t;
		interp = i;
		
		// 设置声明开始符号集
		declbegsys = new BitSet(Symbol.symnum);
		declbegsys.set(Symbol.constsym);
		declbegsys.set(Symbol.varsym);
		declbegsys.set(Symbol.procsym);

		// 设置语句开始符号集
		statbegsys = new BitSet(Symbol.symnum);
		statbegsys.set(Symbol.beginsym);
		statbegsys.set(Symbol.callsym);
		statbegsys.set(Symbol.ifsym);
		statbegsys.set(Symbol.whilesym);

		// 设置因子开始符号集
		facbegsys = new BitSet(Symbol.symnum);
		facbegsys.set(Symbol.ident);
		facbegsys.set(Symbol.number);
		facbegsys.set(Symbol.lparen);

	}
	
	/**
	 * 启动语法分析过程，此前必须先调用一次nextsym()
	 * @see #nextsym()
	 */
	public void parse() {
		BitSet nxtlev = new BitSet(Symbol.symnum);
		nxtlev.or(declbegsys);
		nxtlev.or(statbegsys);
		nxtlev.set(Symbol.period);
		block(0, nxtlev);
		
		if (symtype != Symbol.period)
			Err.report(9);
	}
	
	/**
	 * 获得下一个语法符号，这里只是简单调用一下getsym()
	 */
	public void nextsym() {
		lex.getsym();
		sym =lex.sym;
		symtype = sym.symtype;
	}
	
	/**
	 * 测试当前符号是否合法
	 * 
	 * @param s1 我们需要的符号
	 * @param s2 如果不是我们需要的，则需要一个补救用的集合
	 * @param errcode 错误号
	 */
	void test(BitSet s1, BitSet s2, int errcode) {
		// 在某一部分（如一条语句，一个表达式）将要结束时时我们希望下一个符号属于某集合
		//（该部分的后跟符号），test负责这项检测，并且负责当检测不通过时的补救措施，程
		// 序在需要检测时指定当前需要的符号集合和补救用的集合（如之前未完成部分的后跟符
		// 号），以及检测不通过时的错误号。
		if (!s1.get(symtype)) {
			Err.report(errcode);
			// 当检测不通过时，不停获取符号，直到它属于需要的集合或补救的集合
			while (!s1.get(symtype) && !s2.get(symtype))
				nextsym();
		}
	}
	
	/**
	 * 分析<分程序>
	 * 
	 * @param lev 当前分程序所在层
	 * @param fsys 当前模块后跟符号集
	 */
	public void block(int lev, BitSet fsys) {
		// <分程序> := [<常量说明部分>][<变量说明部分>][<过程说明部分>]<语句>
		
		int dx0, tx0, cx0;				// 保留初始dx，tx和cx
		BitSet nxtlev = new BitSet(Symbol.symnum);
		
		dx0 = dx;						// 记录本层之前的数据量（以便恢复）
		dx = 3;
		tx0 = table.tx;					// 记录本层名字的初始位置（以便恢复）
		table.get(table.tx).adr = interp.cx;
		
		interp.gen(Instr.jmp, 0, 0);
		
		if (lev > PL0.levmax)
			Err.report(32);
		
		// 分析<说明部分>
		do {
			// <常量说明部分>
			if (symtype == Symbol.constsym) {
				nextsym();
				// the original do...while(sym == ident) is problematic, thanks to calculous
				// do
				constdeclaration(lev);
				while (symtype == Symbol.comma) {
					nextsym();
					constdeclaration(lev);
				}
				
				if (symtype == Symbol.semicolon)
					nextsym();
				else
					Err.report(5);				// 漏掉了逗号或者分号
				// } while (sym == ident);
			}
			
			// <变量说明部分>
			if (symtype == Symbol.varsym) {
				nextsym();
				/* the original do...while(sym == ident) is problematic, thanks to calculous */
				/* do {  */
				vardeclaration(lev);
				while (symtype == Symbol.comma)
				{
					nextsym();
					vardeclaration(lev);
				}
				
				if (symtype == Symbol.semicolon)
					nextsym();
				else
					Err.report(5);				// 漏掉了逗号或者分号
				/* } while (sym == ident);  */
			}
			
			// <过程说明部分>
			while (symtype == Symbol.procsym) {
				nextsym();
				if (symtype == Symbol.ident) {
					table.enter(sym, Table.Item.procedur, lev, dx);
					nextsym();
				} else { 
					Err.report(4);				// procedure后应为标识符
				}

				if (symtype == Symbol.semicolon)
					nextsym();
				else
					Err.report(5);				// 漏掉了分号
				
				nxtlev = (BitSet) fsys.clone();
				nxtlev.set(Symbol.semicolon);
				block(lev+1, nxtlev);
				
				if (symtype == Symbol.semicolon) {
					nextsym();
					nxtlev = (BitSet) statbegsys.clone();
					nxtlev.set(Symbol.ident);
					nxtlev.set(Symbol.procsym);
					test(nxtlev, fsys, 6);
				} else { 
					Err.report(5);				// 漏掉了分号
				}
			}
			
			nxtlev = (BitSet) statbegsys.clone(); 
			nxtlev.set(Symbol.ident);
			test(nxtlev, declbegsys, 7);
		} while (declbegsys.get(symtype));		// 直到没有声明符号
		
		// 开始生成当前过程代码
		Table.Item item = table.get(tx0);
		interp.code[item.adr].a = interp.cx;
		item.adr = interp.cx;					// 当前过程代码地址
		item.size = dx;							// 声明部分中每增加一条声明都会给dx增加1，
												// 声明部分已经结束，dx就是当前过程的堆栈帧大小
		cx0 = interp.cx;
		interp.gen(Instr.inte, 0, dx);			// 生成分配内存代码
		
		table.debugTable(tx0);
			
		// 分析<语句>
		nxtlev = (BitSet) fsys.clone();		// 每个后跟符号集和都包含上层后跟符号集和，以便补救
		nxtlev.set(Symbol.semicolon);		// 语句后跟符号为分号或end
		nxtlev.set(Symbol.endsym);
		statement(nxtlev, lev);
		interp.gen(Instr.opr, 0, 0);		// 每个过程出口都要使用的释放数据段指令
		
		nxtlev = new BitSet(Symbol.symnum);	// 分程序没有补救集合
		test(fsys, nxtlev, 8);				// 检测后跟符号正确性
		
		interp.listcode(cx0);
		
		dx = dx0;							// 恢复堆栈帧计数器
		table.tx = tx0;						// 回复名字表位置
	}

	/**
	 * 分析<常量说明部分>
	 * @param lev 当前所在的层次
	 */
	void constdeclaration(int lev) {
		String id;
		if (symtype == Symbol.ident) {
			id = sym.id;
			nextsym();
			if (symtype == Symbol.eql || symtype == Symbol.becomes) {
				if (symtype == Symbol.becomes) 
					Err.report(1);			// 把 = 写成了 :=
				nextsym();
				if (symtype == Symbol.number) {
					sym.id = id;
					table.enter(sym, Table.Item.constant, lev, dx);
					nextsym();
				} else {
					Err.report(2);			// 常量说明 = 后应是数字
				}
			} else {
				Err.report(3);				// 常量说明标识后应是 =
			}
		} else {
			Err.report(4);					// const 后应是标识符
		}
	}

	/**
	 * 分析<变量说明部分>
	 * @param lev 当前层次
	 */
	void vardeclaration(int lev) {
		if (symtype == Symbol.ident) {
			// 填写名字表并改变堆栈帧计数器
			table.enter(sym, Table.Item.variable, lev, dx);
			dx ++;
			nextsym();
		} else {
			Err.report(4);					// var 后应是标识
		}
	}

	/**
	 * 分析<语句>
	 * @param fsys 后跟符号集
	 * @param lev 当前层次
	 */
	void statement(BitSet fsys, int lev) {
		BitSet nxtlev;
		// Wirth 的 PL/0 编译器使用一系列的if...else...来处理
		// 但是你的助教认为下面的写法能够更加清楚地看出这个函数的处理逻辑
		switch (symtype) {
		case Symbol.ident:
			parseAssignStatement(fsys, lev);
			break;
		case Symbol.readsym:
			parseReadStatement(fsys, lev);
			break;
		case Symbol.writesym:
			parseWriteStatement(fsys, lev);
			break;
		case Symbol.callsym:
			parseCallStatement(fsys, lev);
			break;
		case Symbol.ifsym:
			parseIfStatement(fsys, lev);
			break;
		case Symbol.beginsym:
			parseBeginStatement(fsys, lev);
			break;
		case Symbol.whilesym:
			parseWhileStatement(fsys, lev);
			break;
		default:
			nxtlev = new BitSet(Symbol.symnum);
			test(fsys, nxtlev, 19);
			break;
		}
	}

	/**
	 * 分析<当型循环语句>
	 * @param fsys 后跟符号集
	 * @param lev 当前层次
	 */
	private void parseWhileStatement(BitSet fsys, int lev) {
		int cx1, cx2;
		BitSet nxtlev;
		
		cx1 = interp.cx;						// 保存判断条件操作的位置
		nextsym();
		nxtlev = (BitSet) fsys.clone();
		nxtlev.set(Symbol.dosym);				// 后跟符号为do
		condition(nxtlev, lev);					// 分析<条件>
		cx2 = interp.cx;						// 保存循环体的结束的下一个位置
		interp.gen(Instr.jpc, 0, 0);			// 生成条件跳转，但跳出循环的地址未知
		if (symtype == Symbol.dosym)
			nextsym();
		else
			Err.report(18);						// 缺少do
		statement(fsys, lev);					// 分析<语句>
		interp.gen(Instr.jmp, 0, cx1);			// 回头重新判断条件
		interp.code[cx2].a = interp.cx;			// 反填跳出循环的地址，与<条件语句>类似
	}

	/**
	 * 分析<复合语句>
	 * @param fsys 后跟符号集
	 * @param lev 当前层次
	 */
	private void parseBeginStatement(BitSet fsys, int lev) {
		BitSet nxtlev;
		
		nextsym();
		nxtlev = (BitSet) fsys.clone();
		nxtlev.set(Symbol.semicolon);
		nxtlev.set(Symbol.endsym);
		statement(nxtlev, lev);
		// 循环分析{; <语句>}，直到下一个符号不是语句开始符号或收到end
		while (statbegsys.get(symtype) || symtype == Symbol.semicolon) {
			if (symtype == Symbol.semicolon)
				nextsym();
			else
				Err.report(10);					// 缺少分号
			statement(nxtlev, lev);
		}
		if (symtype == Symbol.endsym)
			nextsym();
		else
			Err.report(17);						// 缺少end或分号
	}

	/**
	 * 分析<条件语句>
	 * @param fsys 后跟符号集
	 * @param lev 当前层次
	 */
	private void parseIfStatement(BitSet fsys, int lev) {
		int cx1;
		BitSet nxtlev;
		
		nextsym();
		nxtlev = (BitSet) fsys.clone();
		nxtlev.set(Symbol.thensym);					// 后跟符号为then或do ???
		nxtlev.set(Symbol.dosym);
		condition(nxtlev, lev);						// 分析<条件>
		if (symtype == Symbol.thensym)
			nextsym();
		else
			Err.report(16);							// 缺少then
		cx1 = interp.cx;							// 保存当前指令地址
		interp.gen(Instr.jpc, 0, 0);				// 生成条件跳转指令，跳转地址未知，暂时写0
		statement(fsys, lev);						// 处理then后的语句
		interp.code[cx1].a = interp.cx;				// 经statement处理后，cx为then后语句执行
													// 完的位置，它正是前面未定的跳转地址
	}

	/**
	 * 分析<过程调用语句>
	 * @param fsys 后跟符号集
	 * @param lev 当前层次
	 */
	private void parseCallStatement(BitSet fsys, int lev) {
		int i;
		nextsym();
		if (symtype == Symbol.ident) {
			i = table.position(sym.id);
			if (i == 0) {
				Err.report(11);				// 过程未找到
			} else {
				Table.Item item = table.get(i);
				if (item.kind == Table.Item.procedur)
					interp.gen(Instr.cal, lev - item.level, item.adr);
				else
					Err.report(15);			// call后标识符应为过程
			}
			nextsym();
		} else {
			Err.report(14);					// call后应为标识符
		}
	}

	/**
	 * 分析<写语句>
	 * @param fsys 后跟符号集
	 * @param lev 当前层次
	 */
	private void parseWriteStatement(BitSet fsys, int lev) {
		BitSet nxtlev;

		nextsym();
		if (symtype == Symbol.lparen) {
			do {
				nextsym();
				nxtlev = (BitSet) fsys.clone();
				nxtlev.set(Symbol.rparen);
				nxtlev.set(Symbol.comma);
				expression(nxtlev, lev);
				interp.gen(Instr.opr, 0, 14);
			} while (symtype == Symbol.comma);
			
			if (symtype == Symbol.rparen)
				nextsym();
			else
				Err.report(33);				// write()中应为完整表达式
		}
		interp.gen(Instr.opr, 0, 15);
	}

	/**
	 * 分析<读语句>
	 * @param fsys 后跟符号集
	 * @param lev 当前层次
	 */
	private void parseReadStatement(BitSet fsys, int lev) {
		int i;
		
		nextsym();
		if (symtype == Symbol.lparen) {
			do {
				nextsym();
				if (symtype == Symbol.ident)
					i = table.position(sym.id);
				else
					i = 0;
				
				if (i == 0) {
					Err.report(35);			// read()中应是声明过的变量名
				} else {
					Table.Item item = table.get(i);
					if (item.kind == Table.Item.variable) {
						Err.report(32);		// read()中的标识符不是变量, thanks to amd
					} else {
						interp.gen(Instr.opr, 0, 16);
						interp.gen(Instr.sto, lev-item.level, item.adr);
					}
				}
				
				nextsym();
			} while (symtype == Symbol.comma);
		} else {
			Err.report(34);					// 格式错误，应是左括号
		}
		
		if (symtype == Symbol.rparen) {
			nextsym();
		} else {
			Err.report(33);					// 格式错误，应是右括号
			while (!fsys.get(symtype))
				nextsym();
		}
	}

	/**
	 * 分析<赋值语句>
	 * @param fsys 后跟符号集
	 * @param lev 当前层次
	 */
	private void parseAssignStatement(BitSet fsys, int lev) {
		int i;
		BitSet nxtlev;
		
		i = table.position(sym.id);
		if (i > 0) {
			Table.Item item = table.get(i);
			if (item.kind == Table.Item.variable) {
				nextsym();
				if (symtype == Symbol.becomes)
					nextsym();
				else
					Err.report(13);					// 没有检测到赋值符号
				nxtlev = (BitSet) fsys.clone();
				expression(nxtlev, lev);
				// expression将执行一系列指令，但最终结果将会保存在栈顶，执行sto命令完成赋值
				interp.gen(Instr.sto, lev - item.level, item.adr);
			} else {
				Err.report(12);						// 赋值语句格式错误
			}
		} else {
			Err.report(11);							// 变量未找到
		}
	}

	/**
	 * 分析<表达式>
	 * @param fsys 后跟符号集
	 * @param lev 当前层次
	 */
	private void expression(BitSet fsys, int lev) {
		int addop;
		BitSet nxtlev;
		
		// 分析[+|-]<项>
		if (symtype == Symbol.plus || symtype == Symbol.minus) {
			addop = symtype;
			nextsym();
			nxtlev = (BitSet) fsys.clone();
			nxtlev.set(Symbol.plus);
			nxtlev.set(Symbol.minus);
			term(nxtlev, lev);
			if (addop == Symbol.minus)
				interp.gen(Instr.opr, 0, 1);
		} else {
			nxtlev = (BitSet) fsys.clone();
			nxtlev.set(Symbol.plus);
			nxtlev.set(Symbol.minus);
			term(nxtlev, lev);
		}
		
		// 分析{<加法运算符><项>}
		while (symtype == Symbol.plus || symtype == Symbol.minus) {
			addop = symtype;
			nextsym();
			nxtlev = (BitSet) fsys.clone();
			nxtlev.set(Symbol.plus);
			nxtlev.set(Symbol.minus);
			term(nxtlev, lev);
			if (addop == Symbol.plus)
				interp.gen(Instr.opr, 0, 2);
			else
				interp.gen(Instr.opr, 0, 3);
		}
	}

	/**
	 * 分析<项>
	 * @param fsys 后跟符号集
	 * @param lev 当前层次
	 */
	private void term(BitSet fsys, int lev) {
		int mulop;
		BitSet nxtlev;

		// 分析<因子>
		nxtlev = (BitSet) fsys.clone();
		nxtlev.set(Symbol.times);
		nxtlev.set(Symbol.slash);
		factor(nxtlev, lev);
		
		// 分析{<乘法运算符><因子>}
		while (symtype == Symbol.times || symtype == Symbol.slash) {
			mulop = symtype;
			nextsym();
			factor(nxtlev, lev);
			if (mulop == Symbol.times)
				interp.gen(Instr.opr, 0, 4);
			else
				interp.gen(Instr.opr, 0, 5);
		}
	}

	/**
	 * 分析<因子>
	 * @param fsys 后跟符号集
	 * @param lev 当前层次
	 */
	private void factor(BitSet fsys, int lev) {
		BitSet nxtlev;
		
		test(facbegsys, fsys, 24);			// 检测因子的开始符号
		// the original while... is problematic: var1(var2+var3)
		// thanks to macross
		// while(inset(sym, facbegsys))
		if (facbegsys.get(symtype)) {
			if (symtype == Symbol.ident) {			// 因子为常量或变量
				int i = table.position(sym.id);
				if (i > 0) {
					Table.Item item = table.get(i);
					switch (item.kind) {
					case Table.Item.constant:			// 名字为常量
						interp.gen(Instr.lit, 0, item.val);
						break;
					case Table.Item.variable:			// 名字为变量
						interp.gen(Instr.lod, lev - item.level, item.adr);
						break;
					case Table.Item.procedur:			// 名字为过程
						Err.report(21);				// 不能为过程
						break;
					}
				} else {
					Err.report(11);					// 标识符未声明
				}
				nextsym();
			} else if (symtype == Symbol.number) {	// 因子为数 
				int num = sym.num;
				if (num > PL0.amax) {
					Err.report(31);
					num = 0;
				}
				interp.gen(Instr.lit, 0, num);
				nextsym();
			} else if (symtype == Symbol.lparen) {	// 因子为表达式
				nextsym();
				nxtlev = (BitSet) fsys.clone();
				nxtlev.set(Symbol.rparen);
				expression(nxtlev, lev);
				if (symtype == Symbol.rparen)
					nextsym();
				else
					Err.report(22);					// 缺少右括号
			} else {
				// 做补救措施
				test(fsys, facbegsys, 23);
			}
		}
	}

	/**
	 * 分析<条件>
	 * @param fsys 后跟符号集
	 * @param lev 当前层次
	 */
	private void condition(BitSet fsys, int lev) {
		int relop;
		BitSet nxtlev;
		
		if (symtype == Symbol.oddsym) {
			// 分析 ODD<表达式>
			nextsym();
			expression(fsys, lev);
			interp.gen(Instr.opr, 0, 6);
		} else {
			// 分析<表达式><关系运算符><表达式>
			nxtlev = (BitSet) fsys.clone();
			nxtlev.set(Symbol.eql);
			nxtlev.set(Symbol.neq);
			nxtlev.set(Symbol.lss);
			nxtlev.set(Symbol.leq);
			nxtlev.set(Symbol.gtr);
			nxtlev.set(Symbol.geq);
			expression(nxtlev, lev);
			if (symtype == Symbol.eql || symtype == Symbol.neq 
					|| symtype == Symbol.lss || symtype == Symbol.leq
					|| symtype == Symbol.gtr || symtype == Symbol.geq) {
				relop = symtype;
				nextsym();
				expression(fsys, lev);
				switch (relop) {
				case Symbol.eql:
					interp.gen(Instr.opr, 0, 8);
					break;
				case Symbol.neq:
					interp.gen(Instr.opr, 0, 9);
					break;
				case Symbol.lss:
					interp.gen(Instr.opr, 0, 10);
					break;
				case Symbol.geq:
					interp.gen(Instr.opr, 0, 11);
					break;
				case Symbol.gtr:
					interp.gen(Instr.opr, 0, 12);
					break;
				case Symbol.leq:
					interp.gen(Instr.opr, 0, 13);
					break;
				}
			} else {
				Err.report(20);
			}
		}
	}
	
	void debug(String msg) {
		System.out.println("*** debug : " + msg);
	}
}
