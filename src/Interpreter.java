import java.io.*;

/**
 *����������ӦC���԰汾�е� fct ö�����ͺ� instruction �ṹ�����������ָ��
 */
class Instr {
	// fctö�����͵ĳ���
	public static final int lit = 0;
	public static final int opr = 1;
	public static final int lod = 2;
	public static final int sto = 3;
	public static final int cal = 4;
	public static final int inte= 5;
	public static final int jmp = 6;
	public static final int jpc = 7;
	
	// �����ŵ�����
	public static final String[] mnemonic = new String[]{
			"lit", "opr", "lod", "sto", 
			"cal", "int", "jmp", "jpc"};
	
	/**
	 * ���������ָ��
	 */
	public int f;
	
	/**
	 * ���ò���������Ĳ�β�
	 */
	public int l;
	
	/**
	 * ָ�����
	 */
	public int a;
}

/**
 *������P-Code��������������������ɺ�����������������C���԰���������Ҫ��ȫ�ֱ��� cx �� code
 * @author ��Ӣ��
 *
 */
public class Interpreter {
	// ����ִ��ʱʹ�õ�ջ��С
	final int stacksize = 500;
	
	/**
	 * ���������ָ�룬ȡֵ��Χ[0, cxmax-1] 
	 */
	public int cx = 0;
	
	/**
	 * �����������������
	 */
	public Instr[] code = new Instr[PL0.cxmax];
	
	/**
	 * �������������
	 * @param x instruction.f
	 * @param y instruction.l
	 * @param z instruction.a
	 */
	public void gen(int x, int y, int z) {
		if (cx >= PL0.cxmax) {
			throw new Error("Program too long");
		}
		
		code[cx] = new Instr();
		code[cx].f = x;
		code[cx].l = y;
		code[cx].a = z;
		cx ++;
	}

	/**
	 * ���Ŀ������嵥
	 * @param start ��ʼ�����λ��
	 */
	public void listcode(int start) {
		if (PL0.listswitch) {
			for (int i=start; i<cx; i++) {
				String msg = i + " " + Instr.mnemonic[code[i].f] + " " + code[i].l + " " + code[i].a;
				System.out.println(msg);
				PL0.fa.println(msg);
			}
		}
	}
	
	/**
	 * ���ͳ���
	 */
	public void interpret() {
		BufferedReader stdin = new BufferedReader(new InputStreamReader(System.in));
		int p, b, t;						// ָ��ָ�룬ָ���ַ��ջ��ָ��
		Instr i;							// ��ŵ�ǰָ��
		int[] s = new int[stacksize];		// ջ
		
		System.out.println("start pl0");
		t = b = p = 0;
		s[0] = s[1] = s[2] = 0;
		do {
			i = code[p];					// ����ǰָ��
			p ++;
			switch (i.f) {
			case Instr.lit:				// ��a��ֵȡ��ջ��
				s[t] = i.a;
				t++;
				break;
			case Instr.opr:				// ��ѧ���߼�����
				switch (i.a)
				{
				case 0:
					t = b;
					p = s[t+2];
					b = s[t+1];
					break;
				case 1:
					s[t-1] = -s[t-1];
					break;
				case 2:
					t--;
					s[t-1] = s[t-1]+s[t];
					break;
				case 3:
					t--;
					s[t-1] = s[t-1]-s[t];
					break;
				case 4:
					t--;
					s[t-1] = s[t-1]*s[t];
					break;
				case 5:
					t--;
					s[t-1] = s[t-1]/s[t];
					break;
				case 6:
					s[t-1] = s[t-1]%2;
					break;
				case 8:
					t--;
					s[t-1] = (s[t-1] == s[t] ? 1 : 0);
					break;
				case 9:
					t--;
					s[t-1] = (s[t-1] != s[t] ? 1 : 0);
					break;
				case 10:
					t--;
					s[t-1] = (s[t-1] < s[t] ? 1 : 0);
					break;
				case 11:
					t--;
					s[t-1] = (s[t-1] >= s[t] ? 1 : 0);
					break;
				case 12:
					t--;
					s[t-1] = (s[t-1] > s[t] ? 1 : 0);
					break;
				case 13:
					t--;
					s[t-1] = (s[t-1] <= s[t] ? 1 : 0);
					break;
				case 14:
					System.out.print(s[t-1]);
					PL0.fa2.print(s[t-1]);
					t--;
					break;
				case 15:
					System.out.println();
					PL0.fa2.println();
					break;
				case 16:
					System.out.print("?");
					PL0.fa2.print("?");
					s[t] = 0;
					try {
						s[t] = Integer.parseInt(stdin.readLine());
					} catch (Exception e) {}
					PL0.fa2.println(s[t]);
					t++;
					break;
				}
				break;
			case Instr.lod:				// ȡ��Ե�ǰ���̵����ݻ���ַΪa���ڴ��ֵ��ջ��
				s[t] = s[base(i.l,s,b)+i.a];
				t++;
				break;
			case Instr.sto:				// ջ����ֵ�浽��Ե�ǰ���̵����ݻ���ַΪa���ڴ�
				t--;
				s[base(i.l, s, b) + i.a] = s[t];
				break;
			case Instr.cal:				// �����ӹ���
				s[t] = base(i.l, s, b); 	// ����̬���������ַ��ջ
				s[t+1] = b;					// ����̬���������ַ��ջ
				s[t+2] = p;					// ����ǰָ��ָ����ջ
				b = t;  					// �ı����ַָ��ֵΪ�¹��̵Ļ���ַ
				p = i.a;   					// ��ת
				break;
			case Instr.inte:			// �����ڴ�
				t += i.a;
				break;
			case Instr.jmp:				// ֱ����ת
				p = i.a;
				break;
			case Instr.jpc:				// ������ת����ջ��Ϊ0��ʱ����ת��
				t--;
				if (s[t] == 0)
					p = i.a;
				break;
			}
		} while (p != 0);
	}
	
	/**
	 * ͨ�������Ĳ�β�����øò�Ķ�ջ֡����ַ
	 * @param l Ŀ�����뵱ǰ��εĲ�β�
	 * @param s ����ջ
	 * @param b ��ǰ���ջ֡����ַ
	 * @return Ŀ���εĶ�ջ֡����ַ
	 */
	private int base(int l, int[] s, int b) {
		int b1 = b;
		while (l > 0) {
			b1 = s[b1];
			l --;
		}
		return b1;
	}
}