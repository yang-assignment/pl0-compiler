import java.io.BufferedReader;
import java.io.IOException;

/**
 *�����ʷ�����������Ĺ����Ǵ�Դ���������ȡ�ķ����ţ�����PL/0����������Ҫ��ɲ���֮һ��
 */

public class Scanner {
	/**
	 * �ոն�����ַ�
	 */
	private char ch = ' ';
	
	/**
	 * ��ǰ�������
	 */
	private char[] line;
	
	/**
	 * ��ǰ�еĳ��ȣ�line length��
	 */
	public int ll = 0;
	
	/**
	 * ��ǰ�ַ��ڵ�ǰ���е�λ�ã�character counter��
	 */
	public int cc = 0;
	
	/**
	 * ��ǰ����ķ���
	 */
	public Symbol sym;
	
	/**
	 * �������б���ע�Ᵽ���ֵĴ��˳��
	 */
	private String[] word;
	
	/**
	 * �����ֶ�Ӧ�ķ���ֵ
	 */
	private int[] wsym;
	
	/**
	 * ���ַ��ķ���ֵ
	 */
	private int[] ssym;

	// ������
	private BufferedReader in;
	
	/**
	 * ��ʼ���ʷ�������
	 * @param input PL/0 Դ�ļ�������
	 */
	public Scanner(BufferedReader input) {
		in = input;
		
		// ���õ��ַ�����
		ssym = new int[256];
		java.util.Arrays.fill(ssym, Symbol.nul);
		ssym['+'] = Symbol.plus;
		ssym['-'] = Symbol.minus;
		ssym['*'] = Symbol.times;
		ssym['/'] = Symbol.slash;
		ssym['('] = Symbol.lparen;
		ssym[')'] = Symbol.rparen;
		ssym['='] = Symbol.eql;
		ssym[','] = Symbol.comma;
		ssym['.'] = Symbol.period;
		ssym['#'] = Symbol.neq;
		ssym[';'] = Symbol.semicolon;
		
		// ���ñ���������,������ĸ˳�򣬱����۰����
		word = new String[] {"begin", "call", "const", "do", "end", "if",
			"odd", "procedure", "read", "then", "var", "while", "write"};
		
		// ���ñ����ַ���
		wsym = new int[PL0.norw];
		wsym[0] = Symbol.beginsym;
		wsym[1] = Symbol.callsym;
		wsym[2] = Symbol.constsym;
		wsym[3] = Symbol.dosym;
		wsym[4] = Symbol.endsym;
		wsym[5] = Symbol.ifsym;
		wsym[6] = Symbol.oddsym;
		wsym[7] = Symbol.procsym;
		wsym[8] = Symbol.readsym;
		wsym[9] = Symbol.thensym;
		wsym[10] = Symbol.varsym;
		wsym[11] = Symbol.whilesym;
		wsym[12] = Symbol.writesym;
	}
	
	/**
	 * ��ȡһ���ַ���Ϊ���ٴ���I/O������ÿ�ζ�ȡһ��
	 */
	void getch() {
		String l = "";
		try {
			if (cc == ll) {
				while (l.equals(""))
					l = in.readLine().toLowerCase() + "\n";
				ll = l.length();
				cc = 0;
				line = l.toCharArray();
				System.out.println(PL0.interp.cx + " " + l);
				PL0.fa1.println(PL0.interp.cx + " " + l);
			}
		} catch (IOException e) {
			throw new Error("program imcomplete");
		}
		ch = line[cc];
		cc ++;
	}
	
	/**
	 * �ʷ���������ȡһ���ʷ����ţ��Ǵʷ����������ص�
	 */
	public void getsym() {
		// Wirth �� PL/0 ������ʹ��һϵ�е�if...else...������
		// �������������Ϊ�����д���ܹ���������ؿ�����������Ĵ����߼�
		while (Character.isWhitespace(ch))		// �������пհ��ַ�
			getch();
		if (ch >= 'a' && ch <= 'z') {
			// �ؼ��ֻ���һ���ʶ��
			matchKeywordOrIdentifier();
		} else if (ch >= '0' && ch <= '9') {
			// ����
			matchNumber();
		} else {
			// ������
			matchOperator();
		}
	}
	
	/**
	 * �����ؼ��ֻ���һ���ʶ��
	 */
	void matchKeywordOrIdentifier() {
		int i;
		StringBuilder sb = new StringBuilder(PL0.al);
		// ���Ȱ��������ʶ�����
		do {
			sb.append(ch);
			getch();
		} while (ch >= 'a' && ch <= 'z' || ch >= '0' && ch <= '9');
		String id = sb.toString();
		
		// Ȼ�������ǲ��Ǳ����֣���ע��ʹ�õ���ʲô����������
		i = java.util.Arrays.binarySearch(word, id);
		
		// ����γɷ�����Ϣ
		if (i < 0) {
			// һ���ʶ��
			sym = new Symbol(Symbol.ident);
			sym.id = id;
		} else {
			// �ؼ���
			sym = new Symbol(wsym[i]);
		}
	}
	
	/**
	 * ��������
	 */
	void matchNumber() {
		int k = 0;
		sym = new Symbol(Symbol.number);
		do {
			sym.num = 10*sym.num + Character.digit(ch, 10);
			k++;
			getch();
		} while (ch>='0' && ch<='9'); 				// ��ȡ���ֵ�ֵ
		k--;
		if (k > PL0.nmax)
			Err.report(30);
	}
	
	/**
	 * ����������
	 */
	void matchOperator() {
		// ��ע�������д����Wirth���е㲻ͬ
		switch (ch) {
		case ':':		// ��ֵ����
			getch();
			if (ch == '=') {
				sym = new Symbol(Symbol.becomes);
				getch();
			} else {
				// ����ʶ��ķ���
				sym = new Symbol(Symbol.nul);
			}
			break;
		case '<':		// С�ڻ���С�ڵ���
			getch();
			if (ch == '=') {
				sym = new Symbol(Symbol.leq);
				getch();
			} else {
				sym = new Symbol(Symbol.lss);
			}
			break;
		case '>':		// ���ڻ��ߴ��ڵ���
			getch();
			if (ch == '=') {
				sym = new Symbol(Symbol.geq);
				getch();
			} else {
				sym = new Symbol(Symbol.gtr);
			}
			break;
		default:		// ����Ϊ���ַ���������������ŷǷ��򷵻�nil��
			sym = new Symbol(ssym[ch]);
			if (sym.symtype != Symbol.period)
				getch();
			break;
		}
	}	
}