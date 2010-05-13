/*
 *   Copyright (c) 2008, Ueda Laboratory LMNtal Group <lmntal@ueda.info.waseda.ac.jp>
 *   All rights reserved.
 *
 *   Redistribution and use in source and binary forms, with or without
 *   modification, are permitted provided that the following conditions are
 *   met:
 *
 *    1. Redistributions of source code must retain the above copyright
 *       notice, this list of conditions and the following disclaimer.
 *
 *    2. Redistributions in binary form must reproduce the above copyright
 *       notice, this list of conditions and the following disclaimer in
 *       the documentation and/or other materials provided with the
 *       distribution.
 *
 *    3. Neither the name of the Ueda Laboratory LMNtal Group nor the
 *       names of its contributors may be used to endorse or promote
 *       products derived from this software without specific prior
 *       written permission.
 *
 *   THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 *   "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 *   LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
 *   A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
 *   OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 *   SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 *   LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 *   DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 *   THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 *   (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 *   OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 */

package lmntaleditor.stateviewer;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Shape;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;
import java.awt.geom.Arc2D;
import java.awt.geom.Line2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RectangularShape;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;

import javax.imageio.ImageIO;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.event.MouseInputListener;

import lmntaleditor.*;
import lmntaleditor.runner.LmntalRunner;
import lmntaleditor.util.CommonFontUser;

public class StateGraphPanel extends JPanel implements MouseInputListener,MouseWheelListener,KeyListener,CommonFontUser{

	public StatePanel statePanel;

	private StateNodeSet drawNodes;

	private ArrayList<StateNode> selectNodes;
	private boolean nodeSelected;

	private double zoom;
	private double drawTime;

	private boolean simpleMode;

	private Point lastPoint;
	private Point startPoint;

	private StatePainter painter;
	private StateDynamicMover mover;

	private JTextField clickStringField;

	public StateGraphPanel(StatePanel statePanel){
		this.statePanel = statePanel;

		setFocusable(true);
		setLayout(new BorderLayout());

		clickStringField = new JTextField();
		clickStringField.setVisible(false);
		add(clickStringField, BorderLayout.SOUTH);

		selectNodes  = new ArrayList<StateNode>();

		painter = new StatePainter(this);
		painter.start();

		mover = new StateDynamicMover(this);
		mover.start();

		loadFont();
		FrontEnd.addFontUser(this);
	}

	public void init(StateNodeSet nodes){

		this.drawNodes = nodes;

		this.selectNodes.clear();
		this.nodeSelected = false;

		this.zoom = 1.0;
		this.drawTime = 0.0;

		this.simpleMode = false;

		this.lastPoint = null;

		positionReset();
		setActive(true);
		//adjustReset();
	}

	void update(){
		painter.update();
	}

	void setActive(boolean active){
		if(painter.isActive()==active){ return; }
		if(active){
			addMouseListener(this);
			addMouseMotionListener(this);
			addMouseWheelListener(this);
			addKeyListener(this);
		}else{
			removeMouseListener(this);
			removeMouseMotionListener(this);
			removeMouseWheelListener(this);
			removeKeyListener(this);
		}
		painter.setActive(active);
		mover.setActive(active&& Env.is("SV_DYNAMIC_MOVER"));

		statePanel.stateControlPanel.allButtonSetEnabled(active);
		if(active){
			update();
		}else{
			repaint();
		}
	}

	void setDynamicMoverActive(boolean active){
		mover.setActive(active);
	}

	StateDynamicMover getDynamicMover(){
		return mover;
	}

	public void loadFont(){
		Font font = new Font(Env.get("EDITER_FONT_FAMILY"), Font.PLAIN, Env.getInt("EDITER_FONT_SIZE"));
		clickStringField.setFont(font);
		revalidate();
	}

	void setInnerZoom(double zoom){
		if(zoom<0.0001){ zoom=0.0001; }else if(zoom>4.0){ zoom=4.0; }

		double w = (double)getWidth() / 2;
		double h = (double)getHeight() / 2;
		double newW = w / zoom;
		double newH = h / zoom;
		double oldW = w / this.zoom;
		double oldH = h / this.zoom;

		drawNodes.allMove(newW-oldW,newH-oldH);

		this.zoom = zoom;
	}

	public void setZoom(double zoom){
		setInnerZoom(zoom);
		statePanel.stateControlPanel.setSliderPos(zoom);
	}

	public double getZoom(){
		return this.zoom;
	}

	public double getDrawTime(){
		return this.drawTime;
	}

	public int getDepth(){
		if(drawNodes==null) return 0;
		return drawNodes.getDepth();
	}

	public int getAllNum(){
		if(drawNodes==null) return 0;
		return drawNodes.size()-drawNodes.getDummySize();
	}

	public int getEndNum(){
		if(drawNodes==null) return 0;
		return drawNodes.getEndNode().size();
	}

	public ArrayList<StateNode> getSelectNodes(){
		return selectNodes;
	}

	public StateNodeSet getDrawNodes(){
		return drawNodes;
	}

	/*
	void oldPositionReset(){
		double w = (double)getWidth();
		double h = (double)getHeight();

		setCenterZoom(1.0);
		xPosInterval = w/(drawNodes.getDepth()+1);
		yPosInterval = new double[drawNodes.getDepth()];
		for(int i=0;i<drawNodes.getDepth();++i){
			yPosInterval[i] = h/(drawNodes.getSameDepthSize(i)+1);
		}

		for(StateNode node : drawNodes.getAllNode()){
			node.resetLocation(xPosInterval,yPosInterval);
		}
		update();
	}

	void oldLengthReset(){
		double w = (double)getWidth();
		double h = (double)getHeight();

		double minLength = w/(drawNodes.getDepth()+1);
		for(int i=0;i<drawNodes.getDepth();++i){
			double d = h/(drawNodes.getSameDepthSize(i)+1);
			if(d<minLength) minLength = d;
		}
		setCenterZoom(minLength/30);
		xPosInterval = w/(drawNodes.getDepth()+1);
		xPosInterval /= getZoom();
		yPosInterval = new double[drawNodes.getDepth()];
		for(int i=0;i<drawNodes.getDepth();++i){
			yPosInterval[i] = h/(drawNodes.getSameDepthSize(i)+1);
			yPosInterval[i] /= getZoom();
		}

		for(StateNode node : drawNodes.getAllNode()){
			node.resetLocation(xPosInterval,yPosInterval);
		}
		update();
	}
	*/

	void positionReset(){
		double w = (double)getWidth();
		double h = (double)getHeight();

		double minLength = w/(drawNodes.getDepth()+1);
		for(int i=0;i<drawNodes.getDepth();++i){
			double d = h/(drawNodes.getSameDepthSize(i)+1);
			if(d<minLength) minLength = d;
		}
		if(minLength/30>1){
			setZoom(minLength/30);
		}else{
			setZoom(1.0);
		}

		double xPosInterval;
		xPosInterval = w/(drawNodes.getDepth()+1);
		xPosInterval /= zoom;

		double[] yPosInterval = new double[drawNodes.getDepth()];
		for(int i=0;i<drawNodes.getDepth();++i){
			yPosInterval[i] = h/(drawNodes.getSameDepthSize(i)+1);
			yPosInterval[i] /= zoom;
		}

		for(StateNode node : drawNodes.getAllNode()){
			node.resetLocation(xPosInterval,yPosInterval);
		}
		//cross = (new StateGraphExchangeWorker(this)).getAllCross();
		autoCentering();
		update();
	}

	void adjustReset(){
		StateGraphAdjustWorker worker = new StateGraphAdjustWorker(this);
		worker.ready();
		worker.execute();
	}

	void adjust2Reset(){
		StateGraphAdjust2Worker worker = new StateGraphAdjust2Worker(this);
		worker.ready();
		worker.execute();
	}

	void adjust3Reset(){
		StateGraphAdjust3Worker worker = new StateGraphAdjust3Worker(this);
		worker.ready();
		worker.execute();
	}

	void exchangeReset(){
		StateGraphExchangeWorker worker = new StateGraphExchangeWorker(this);
		worker.ready();
		worker.execute();
	}

	void geneticAlgorithmLength(){
		StateGraphGeneticAlgorithmWorker worker = new StateGraphGeneticAlgorithmWorker(this);
		worker.ready();
		worker.execute();
	}

	void stretchMove(){
		StateGraphStretchMoveWorker worker = new StateGraphStretchMoveWorker(this);
		worker.ready();
		worker.execute();
	}

	void randomMove(){
		StateGraphRandomMoveWorker worker = new StateGraphRandomMoveWorker(this);
		worker.ready();
		worker.execute();
	}

	void groupMove(){
		for(int i=0;i<5;++i){
			for(StateNode node : drawNodes.getAllNode()){
				double y=0;
				int count=0;
				for(StateNode to : node.getToNodes()){
					y+=to.getY();
					++count;
				}
				for(StateNode from : node.getFromNodes()){
					y+=from.getY();
					++count;
				}
				node.setPosition(node.getX(),y/count);
			}
		}
		update();
	}

	public void startMover(){
		mover.setActive(!mover.isActive());
	}

	public void autoCentering(){
		zoomCentering();
		moveCentering();
		update();
	}

	private void zoomCentering(){
		Rectangle2D.Double d = drawNodes.getNodesDimension();

		double zoomX = getWidth()/d.width;
		double zoomY = getHeight()/d.height;

		if(zoomX<zoomY){
			setZoom(zoomX);
		}else{
			setZoom(zoomY);
		}
	}

	private void moveCentering(){
		drawNodes.resetMovePosition();
		Rectangle2D.Double d = getNodesWindowDimension();
		drawNodes.allMove(((getWidth()-d.width)/2.0)/zoom,((getHeight()-d.height)/2.0)/zoom);
	}


	public void dummyCentering(){
		ArrayList<ArrayList<StateNode>> depthNode = drawNodes.getDepthNode();
		for(ArrayList<StateNode> nodes : depthNode){
			ArrayList<StateNode> ns = new ArrayList<StateNode>();
			StateNode startNode = null;

			nodes = new ArrayList<StateNode>(nodes);
			Collections.sort(nodes, new Comparator<StateNode>() {
				public int compare(StateNode n1, StateNode n2) {
					if(n1.getY()<n2.getY()){
						return -1;
					}else if(n1.getY()>n2.getY()){
						return 1;
					}else{
						if(n1.no<n2.no){
							return -1;
						}else if(n1.no>n2.no){
							return 1;
						}else{
							return 0;
						}
					}
				}
			});

			for(StateNode node : nodes){
				if(node.dummy){
					ns.add(node);
				}else{
					if(ns.size()>0){
						if(startNode==null){
							double startY = node.getY()-node.getRadius();
							double intarval = 15;
							for(int i=0;i<ns.size();++i){
								ns.get(i).setY(startY+intarval*(i-ns.size()));
							}
						}else{
							double startY = startNode.getY()+startNode.getRadius();
							double endY = node.getY()-node.getRadius();
							double intarval = (endY-startY)/(double)(ns.size()+1);
							for(int i=0;i<ns.size();++i){
								ns.get(i).setY(startY+intarval*(i+1));
							}
						}
						ns.clear();
					}
					startNode = node;
				}
			}

			if(startNode!=null){
				double startY = startNode.getY()+startNode.getRadius();
				double intarval = 15;
				for(int i=0;i<ns.size();++i){
					ns.get(i).setY(startY+intarval*(i+1));
				}
			}

		}
		update();
	}

	public void dummySmoothing(){
		StateGraphDummySmoothingWorker worker = new StateGraphDummySmoothingWorker(this);
		worker.ready();
		worker.execute();
	}

	public Rectangle2D.Double getNodesWindowDimension(){
		Rectangle2D.Double d = drawNodes.getNodesDimension();
		d.x *= zoom;
		d.y *= zoom;
		d.width *= zoom;
		d.height *= zoom;
		return d;
	}

	public int stateFind(String str){

		int match = 0;
		selectNodes.clear();
		nodeSelected = false;
		for(StateNode node : drawNodes.getAllNode()){ node.weak = true; node.updateLooks(); }

		if(str.equals("")){
			searchReset();
			return 0;
		}

		for(StateNode node : drawNodes.getAllNode()){
			if(node.isMatch(str)){
				drawNodes.setOrderEnd(node);
				node.weak = false;
				node.updateLooks();
				match++;
			}
		}
		update();
		return match;
	}

	public int stateMatch(String head,String guard){

		int match = 0;
		selectNodes.clear();
		nodeSelected = false;
		for(StateNode node : drawNodes.getAllNode()){ node.weak = true; node.updateLooks(); }

		if(head.equals("")&&guard.equals("")){
			searchReset();
			return 0;
		}

		File f = new File("temp.lmn");
		try {
			FileWriter fp = new FileWriter(f);
			fp.write(drawNodes.getMatchFileString(head,guard));
            fp.close();
		} catch (IOException e) {
			FrontEnd.printException(e);
		}

		final LmntalRunner lr = new LmntalRunner("",f);
		lr.setBuffering(true);
		lr.run();

		while(lr.isRunning()){
			FrontEnd.sleep(200);
		}
		if(lr.isSuccess()){
			String str = lr.getBufferString();
			int sp=str.indexOf("sVr_matches{");
			if(sp==-1){ return -1; }
			int ep=str.indexOf("}",sp+12);
			String ids = str.substring(sp+12,ep);
			if(ids.length()==0){
				match = 0;
			}else{
				String[] strs = ids.split(",");
				for(String s: strs){
					long id = Long.parseLong(s.substring(s.indexOf("(")+1,s.indexOf(")")));
					StateNode node = drawNodes.get(id);
					drawNodes.setOrderEnd(node);
					node.weak = false;
					node.updateLooks();
					match++;
				}
			}
		}else{
			searchReset();
			return -1;
		}

		update();
		return match;
	}

	void searchShortCycle(){
		ArrayList<StateNode> cycles = new ArrayList<StateNode>();
		StateNode node = drawNodes.getStartNode();
		StateNode cyclestart = null;
		boolean loop = false;
		while(node != null){
			if(cycles.contains(node)){
				cyclestart = node;
				loop = true;
				break;
			}
			if(node.inCycle){ cycles.add(node); }
			node = node.getEmToNode();
		}
		if(cycles.size()==0){ return; }
		if(cyclestart==null){
			cyclestart = cycles.get(cycles.size()-1);
		}

		//ループしているノードの探索
		ArrayList<StateNode> loopNodes = new ArrayList<StateNode>();
		if(loop){
			node = cyclestart.getEmToNode();
			while(node != cyclestart){
				loopNodes.add(node);
				node = node.getEmToNode();
			}
		}
		loopNodes.add(cyclestart);


		if(loop){
			//ループを縮小
			while(true){
				ArrayList<StateNode> newLoopNodes = new ArrayList<StateNode>();
				for(StateNode n : loopNodes){ newLoopNodes.add(n); }
				m : for(int i=0;i<newLoopNodes.size();++i){
					StateNode n = newLoopNodes.get(i);
					int size = newLoopNodes.size();
					for(int j=(i+2)%size;j!=i;){
						if(n.getToNodes().contains(newLoopNodes.get(j))){
							ArrayList<StateNode> removeLoopNodes = new ArrayList<StateNode>();
							for(int k=(i+1)%size;k!=j;){
								removeLoopNodes.add(newLoopNodes.get(k));
								k = (k+1)%size;
							}
							for(StateNode m : removeLoopNodes){
								newLoopNodes.remove(m);
							}
							break m;
						}
						j = (j+1)%size;
					}
				}
				if(loopNodes.size()==newLoopNodes.size()){ break; }
				boolean hit = false;
				for(StateNode n : newLoopNodes){
					if(n.accept){
						hit = true;
						break;
					}
				}
				if(hit){
					loopNodes = newLoopNodes;
				}else{
					break;
				}
			}

			//ループ内の強調を更新
			for(int i=0;i<loopNodes.size();++i){
				StateNode n = loopNodes.get(i);
				StateNode t = loopNodes.get((i+1)%loopNodes.size());
				n.resetEmToNode();
				n.setEmToNode(t,true);
			}
		}

		//ループ中以外は不受理化
		for(StateNode n : drawNodes.getAllNode()){
			if(loopNodes.contains(n)){
				n.inCycle = true;
				n.weak = false;
				drawNodes.setOrderEnd(n);
			}else{
				n.inCycle = false;
				n.weak = true;
				n.resetEmToNode();
			}
			n.updateLooks();
		}

		//ループの入口から初期状態まで最短で受理化
		StateNode tn = cyclestart;
		node = cyclestart.getOneFromNode();
		while(node!=null){
			node.inCycle = true;
			node.weak = false;
			node.setEmToNode(tn,true);
			node.updateLooks();
			drawNodes.setOrderEnd(node);
			tn = node;
			node = node.getOneFromNode();
		}

		update();
	}

	void searchReset(){
		for(StateNode node : drawNodes.getAllNode()){
			node.weak = false;
			node.inCycle = false;
			node.resetEmToNode();
			node.updateLooks();
		}
		update();
	}

	void emBackNodes(ArrayList<StateNode> nodes){
		ArrayList<StateNode> weaks = new ArrayList<StateNode>(drawNodes.getAllNode());
		drawNodes.allNodeUnMark();

		LinkedList<StateNode> queue = new LinkedList<StateNode>();

		for(StateNode n : nodes){
			n.mark();
			queue.add(n);
			weaks.remove(n);
		}

		while(!queue.isEmpty()){
			StateNode node = queue.remove();
			for(StateNode n : node.getFromNodes()){
				if(n.isMarked()||n.depth!=node.depth-1){continue;}
				n.mark();
				queue.add(n);
				weaks.remove(n);
			}
		}
		for(StateNode node : weaks){ node.weak = true; node.updateLooks(); }
		update();
	}

	void emFromNodes(ArrayList<StateNode> nodes){
		ArrayList<StateNode> weaks = new ArrayList<StateNode>(drawNodes.getAllNode());
		drawNodes.allNodeUnMark();

		LinkedList<StateNode> queue = new LinkedList<StateNode>();

		for(StateNode n : nodes){
			n.mark();
			queue.add(n);
			weaks.remove(n);
		}

		while(!queue.isEmpty()){
			StateNode node = queue.remove();
			for(StateNode n : node.getFromNodes()){
				if(n.isMarked()){continue;}
				n.mark();
				queue.add(n);
				weaks.remove(n);
			}
		}
		for(StateNode node : weaks){ node.weak = true; node.updateLooks(); }
		update();
	}

	void emNextNodes(ArrayList<StateNode> nodes){
		ArrayList<StateNode> weaks = new ArrayList<StateNode>(drawNodes.getAllNode());
		drawNodes.allNodeUnMark();

		LinkedList<StateNode> queue = new LinkedList<StateNode>();

		for(StateNode n : nodes){
			n.mark();
			queue.add(n);
			weaks.remove(n);
		}

		while(!queue.isEmpty()){
			StateNode node = queue.remove();
			for(StateNode n : node.getToNodes()){
				if(n.isMarked()||node.depth+1!=n.depth){continue;}
				n.mark();
				queue.add(n);
				weaks.remove(n);
			}
		}
		for(StateNode node : weaks){ node.weak = true; node.updateLooks(); }
		update();
	}

	void emToNodes(ArrayList<StateNode> nodes){
		ArrayList<StateNode> weaks = new ArrayList<StateNode>(drawNodes.getAllNode());
		drawNodes.allNodeUnMark();

		LinkedList<StateNode> queue = new LinkedList<StateNode>();

		for(StateNode n : nodes){
			n.mark();
			queue.add(n);
			weaks.remove(n);
		}

		while(!queue.isEmpty()){
			StateNode node = queue.remove();
			for(StateNode n : node.getToNodes()){
				if(n.isMarked()){continue;}
				n.mark();
				queue.add(n);
				weaks.remove(n);
			}
		}
		for(StateNode node : weaks){ node.weak = true; node.updateLooks(); }
		update();
	}

	public void emTransitions(ArrayList<StateTransition> trans){
		ArrayList<StateNode> weaks = new ArrayList<StateNode>(drawNodes.getAllNode());

		for(StateTransition t : trans){
			t.from.inCycle = true;
			weaks.remove(t.from);

			t.to.inCycle = true;
			weaks.remove(t.to);

			t.from.setEmToNode(t.to, true);
		}

		for(StateNode node : weaks){ node.weak = true; node.updateLooks(); }
		update();
	}

	private void setStateString(String str){
		if(str==null){
			clickStringField.setVisible(false);
		}else{
			clickStringField.setVisible(true);
			clickStringField.setText(str);
			validate();
		}
	}

	public void allDelete(){
		setActive(false);
		statePanel.stateControlPanel.updateInfo();
		repaint();
	}

	public void paintComponent(Graphics g){
		Graphics2D g2 = (Graphics2D)g;

		//フレームの初期化
		g2.setColor(Color.white);
		g2.fillRect(0, 0, getWidth(), getHeight());

		if(!painter.isActive()){ return; }

		double startTime = System.currentTimeMillis();
		this.simpleMode = zoom<=0.7;

		//描写対象の決定
		double minX=-10/zoom,maxX=(getWidth()+10)/zoom;
		double minY=-10/zoom,maxY=(getHeight()+10)/zoom;
		for(StateNode node : drawNodes.getAllNodeDrawOrder()){
			node.setInFrame(false);
			if(minX<node.getX()&&node.getX()<maxX&&minY<node.getY()&&node.getY()<maxY){
				node.setInFrame(true);
			}
		}

		g2.scale(zoom,zoom);

		//初期状態の矢印の描写
		drawStartArrow(g2);

		//線の描写
		//for(StateNode node : drawNodes.getAllNodeDrawOrder()){
		//	drawArrow(g2,node);
		//}
		for(StateTransition t : drawNodes.getAllTransitionDrawOrder()){
			drawTransition(g2,t);
		}

		/*
		{
			LinkedList<StateNode> queue = new LinkedList<StateNode>();
			drawNodes.allNodeUnMark();

			queue.add(drawNodes.getStartNode());
			drawNodes.getStartNode().mark();

			while(!queue.isEmpty()){

			}
		}
		*/

		//ノードの描写
		for(StateNode node : drawNodes.getAllNodeDrawOrder()){
			drawNode(g2,node);
		}

		//選択しているノードの描写
		for(StateNode node : selectNodes){
			drawSelectNodeAndLink(g2,node);
		}
		if(selectNodes.size()==1){
			setStateString(selectNodes.get(0).state);
		}else{
			setStateString(null);
		}

		g2.scale(1.0/zoom, 1.0/zoom);

		drawTime = System.currentTimeMillis()-startTime;
		statePanel.stateControlPanel.updateInfo();

		//System.out.println("SV  _PAINT="+System.currentTimeMillis());

	}

	private void drawStartArrow(Graphics2D g2){
		StateNode node = drawNodes.getStartNode();
		if(node==null){ return; }
		if(!node.isInFrame()){ return; }

		if(node.weak){
			g2.setColor(Color.lightGray);
		}else{
			g2.setColor(Color.black);
		}

		drawNodeArrow(g2,node.getX()-30,node.getY(),node.getRadius(),node.getX()-7,node.getY(),node.getRadius(),5);
	}

	private void drawArrow(Graphics2D g2,StateNode node){

		//遷移先への矢印を表示
		for(StateNode to : node.getToNodes()){
			if(Env.is("SV_HIDEBACKEDGE")&&to.depth<node.depth){ continue; }
			if(!node.isInFrame()&&!to.isInFrame()){ continue; }
			if(node.weak||to.weak){
				g2.setColor(Color.lightGray);
			}else if(!node.isEmToNode(to)&&(node.inCycle||to.inCycle)){
				g2.setColor(Color.lightGray);
			}else{
				g2.setColor(Color.black);
			}
			if(!simpleMode){
				if(to!=node){
					if(to.dummy){
						drawLine(g2,node.getX(),node.getY(),to.getX(),to.getY());
					}else{
						drawNodeArrow(g2,node.getX(),node.getY(),node.getRadius(),to.getX(),to.getY(),to.getRadius(),5);
					}
				}else{
					drawSelfArrow(g2,node);
				}

				if((Env.is("SV_SHOWRULE")||Env.is("SV_SHOWNONAMERULE"))&&!node.dummy){
					String str = node.getToRuleName(to);
					if(str.length()>0){
						if((!str.substring(0, 1).equals("_")&&Env.is("SV_SHOWRULE"))||(str.substring(0, 1).equals("_")&&Env.is("SV_SHOWNONAMERULE"))){
							FontMetrics fm = g2.getFontMetrics();
							int h = 0;
							if(node.depth>to.depth){
								h = fm.getHeight();
							}
							g2.drawString(str,(int)((node.getX()+to.getX())/2)-fm.stringWidth(str)/2,(int)((node.getY()+to.getY())/2)+h);
						}
					}
				}

			}else{
				if(to!=node){
					drawLine(g2,node.getX(),node.getY(),to.getX(),to.getY());
				}
			}
		}
	}

	private void drawTransition(Graphics2D g2,StateTransition t){

		StateNode node = t.from;
		StateNode to = t.to;

		if(Env.is("SV_HIDEBACKEDGE")&&to.depth<node.depth){ return; }
		if(!node.isInFrame()&&!to.isInFrame()){ return; }
		if(node.weak||to.weak){
			g2.setColor(Color.lightGray);
		}else if(!node.isEmToNode(to)&&(node.inCycle&&to.inCycle)){
			g2.setColor(Color.lightGray);
		}else{
			g2.setColor(Color.black);
		}
		if(!simpleMode){
			if(to!=node){
				if(to.dummy){
					drawLine(g2,node.getX(),node.getY(),to.getX(),to.getY());
				}else{
					drawNodeArrow(g2,node.getX(),node.getY(),node.getRadius(),to.getX(),to.getY(),to.getRadius(),5);
				}
			}else{
				drawSelfArrow(g2,node);
			}
			if((Env.is("SV_SHOWRULE")||Env.is("SV_SHOWNONAMERULE"))&&!node.dummy){
				String str = node.getToRuleName(to);
				if(str.length()>0){
					if((!str.substring(0, 1).equals("_")&&Env.is("SV_SHOWRULE"))||(str.substring(0, 1).equals("_")&&Env.is("SV_SHOWNONAMERULE"))){
						FontMetrics fm = g2.getFontMetrics();
						int h = 0;
						if(node.depth>to.depth){
							h = fm.getHeight();
						}
						g2.drawString(str,(int)((node.getX()+to.getX())/2)-fm.stringWidth(str)/2,(int)((node.getY()+to.getY())/2)+h);
					}
				}
			}
		}else{
			if(to!=node){
				drawLine(g2,node.getX(),node.getY(),to.getX(),to.getY());
			}
		}
	}

	private void drawNode(Graphics2D g2,StateNode node){
		if(!node.isInFrame()){ return; }
		if(node.dummy){
			if(Env.is("SV_SHOW_DUMMY")){
				double r = 2.0;
				g2.setColor(Color.gray);
				g2.fill(new RoundRectangle2D.Double(node.getX()-r,node.getY()-r,r*2,r*2,r*2,r*2));
			}
			return;
		}
		g2.setColor(node.getFillColor());
		g2.fill(node);

		if(!simpleMode){
			g2.setColor(node.getDrawColor());
			g2.draw(node);
			if(node.isAccept()&&!node.dummy){
				double r = node.getRadius()-2.0;
				g2.draw(new RoundRectangle2D.Double(node.getX()-r,node.getY()-r,r*2,r*2,r*2,r*2));
			}
		}
	}

	private void drawSelectNodeAndLink(Graphics2D g2,StateNode node){

		// 遷移元の表示
		g2.setColor(Color.BLUE);
		for(StateNode from : node.getFromNodes()){
			StateNode to = node;
			if(from==to){ continue; }
			if(from.dummy){
				while(from.dummy){
					to = from;
					from = from.getFromNodes().get(0);
				}
				while(to.dummy){
					drawNodeLine(g2,from.getX(),from.getY(),from.getRadius(),to.getX(),to.getY(),to.getRadius());
					from = to;
					to = to.getToNodes().get(0);
				}
			}
			if(!simpleMode){
				drawNodeArrow(g2,from.getX(),from.getY(),from.getRadius(),to.getX(),to.getY(),to.getRadius(),5);
			}else{
				drawLine(g2,from.getX(),from.getY(),to.getX(),to.getY());
			}
		}

		// 遷移先の表示
		g2.setColor(Color.RED);
		for(StateNode to : node.getToNodes()){
			StateNode from = node;
			if(to.dummy){
				while(to.dummy){
					drawNodeLine(g2,from.getX(),from.getY(),from.getRadius(),to.getX(),to.getY(),to.getRadius());
					from = to;
					to = to.getToNodes().get(0);
				}
			}
			if(to==from){
				drawSelfArrow(g2,from);
			}else{
				if(!simpleMode){
					drawNodeArrow(g2,from.getX(),from.getY(),from.getRadius(),to.getX(),to.getY(),to.getRadius(),5);
				}else{
					drawLine(g2,from.getX(),from.getY(),to.getX(),to.getY());
				}
				//戻り矢印があるかチェック
				for(StateNode t : to.getToNodes()){
					if(t==from){
						if(!simpleMode){
							drawNodeArrow(g2,to.getX(),to.getY(),to.getRadius(),from.getX(),from.getY(),from.getRadius(),5);
						}else{
							drawLine(g2,to.getX(),to.getY(),from.getX(),from.getY());
						}
						break;
					}
				}
			}

		}

		g2.setColor(node.getFillColor());
		g2.fill(node);
		g2.setColor(Color.RED);
		g2.draw(node);
	}

	private void drawSelfArrow(Graphics2D g2,StateNode node){
		double radius = node.getRadius();
		drawArc(g2,node.getX()-radius*2+1,node.getY()-radius*2+1,radius*2-1,radius*2-1,0,270);
		drawLine(g2,node.getX()-radius-1,node.getY(),node.getX()-radius-1,node.getY()-3);
		drawLine(g2,node.getX()-radius-1,node.getY(),node.getX()-radius-3,node.getY()+1);
	}

	private void drawNodeArrow(Graphics2D g2,double x1,double y1,double r1,double x2,double y2,double r2,double a){
		double theta = Math.atan2((double)(y2-y1),(double)(x2-x1));

		double cos = Math.cos(theta);
		double sin = Math.sin(theta);

		double startX = x1+(r1+1)*cos;
		double startY = y1+(r1+1)*sin;
		double endX = x2-(r2+1)*cos;
		double endY = y2-(r2+1)*sin;

		double dts = (2.0 * Math.PI / 360.0) * 30;

		drawLine(g2,startX,startY,endX,endY);
		drawLine(g2,endX,endY,endX-a*Math.cos(theta-dts),endY-a*Math.sin(theta-dts));
		drawLine(g2,endX,endY,endX-a*Math.cos(theta+dts),endY-a*Math.sin(theta+dts));
	}

	private void drawNodeLine(Graphics2D g2,double x1,double y1,double r1,double x2,double y2,double r2){
		double theta = Math.atan2((double)(y2-y1),(double)(x2-x1));

		double cos = Math.cos(theta);
		double sin = Math.sin(theta);

		double startX = x1+r1*cos;
		double startY = y1+r1*sin;
		double endX = x2-r2*cos;
		double endY = y2-r2*sin;

		drawLine(g2,startX,startY,endX,endY);
	}

	private void drawLine(Graphics2D g2,double x1,double y1,double x2,double y2){
		if(zoom>2.0){
			//doubleライン
			g2.draw(new Line2D.Double(x1,y1,x2,y2));
		}else{
			//intライン
			g2.drawLine((int)x1, (int)y1, (int)x2, (int)y2);
		}
	}

	private void drawArc(Graphics2D g2,double x,double y,double w,double h,double start,double extent){
		if(zoom>2.0){
			//doubleアーク
			g2.draw(new Arc2D.Double(x,y,w,h,start,extent,Arc2D.OPEN));
		}else{
			//intアーク
			g2.drawArc((int)x,(int)y,(int)w,(int)h,(int)start,(int)extent);
		}
	}

	/*
	void reduction(){
		drawNodes.reduction();
		update();
	}
	*/

	public void shakeMove(){
		for(int i=0;i<50;++i){
			for(StateNode node : drawNodes.getAllNode()){
				double newX = (Math.random()-0.5)*20.0 + node.getX();
				double newY = (Math.random()-0.5)*20.0 + node.getY();
				double nowMinLength = drawNodes.getMinLength(node.id,node.getX(),node.getY());
				double newMinLength = drawNodes.getMinLength(node.id,newX,newY);
				if(nowMinLength<24&&nowMinLength<newMinLength){
					node.setPosition(newX, newY);
				}else if(newMinLength>=24){
					double nowLinkLength = drawNodes.getLinkLength(node,node.getX(),node.getY());
					double newLinkLength = drawNodes.getLinkLength(node,newX,newY);
					if(newLinkLength<nowLinkLength){
						node.setPosition(newX, newY);
					}
				}
			}
		}
		autoCentering();
	}

	public boolean isDragg(){
		if(lastPoint==null){
			return false;
		}else{
			return true;
		}
	}

	public void mouseClicked(MouseEvent e) {

	}

	public void mouseEntered(MouseEvent e) {

	}

	public void mouseExited(MouseEvent e) {

	}

	public void mousePressed(MouseEvent e) {
		requestFocus();

		lastPoint = e.getPoint();
		startPoint = e.getPoint();

		Point p = new Point((int)((double)e.getX()/zoom), (int)((double)e.getY()/zoom));
		StateNode selectNode = drawNodes.pickANode(p);

		if(e.isControlDown()){
			if(selectNode!=null){
				if(!selectNodes.contains(selectNode)){
					selectNodes.add(selectNode);
					nodeSelected = true;
				}else{
					selectNodes.remove(selectNode);
					nodeSelected = false;
				}
			}else{
				nodeSelected = false;
			}
		}else{
			if(selectNode!=null){
				if(!selectNodes.contains(selectNode)){
					selectNodes.clear();
					selectNodes.add(selectNode);
				}else if(e.getClickCount()==2){
					selectNode.showFrame(e.getXOnScreen(), e.getYOnScreen());
				}
				nodeSelected = true;
			}else{
				selectNodes.clear();
				nodeSelected = false;
			}
		}
		if(SwingUtilities.isRightMouseButton(e)){
			(new StateRightMenu(this)).show(e.getComponent(), e.getX(), e.getY());
		}
		update();
	}

	public void mouseReleased(MouseEvent e) {
		lastPoint = null;
		startPoint = null;
		setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
	}

	public void mouseDragged(MouseEvent e) {
		if(lastPoint == null){
			lastPoint = e.getPoint();
			return;
		}
		if(startPoint == null){
			startPoint = e.getPoint();
			return;
		}
		double dx = (double)(e.getX() - lastPoint.x) / zoom;
		double dy = (double)(e.getY() - lastPoint.y) / zoom;

		if(nodeSelected){
			if(!e.isShiftDown()){ dx = 0; }
			if(e.isAltDown()&&selectNodes.size()==1){
				for(StateNode node : selectNodes){
					accompany_move(node,dx,dy);
				}
			}else{
				for(StateNode node : selectNodes){
					node.move(dx, dy);
				}
			}
		}else{
			setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
			if(e.isShiftDown()){
				Rectangle2D.Double d = getNodesWindowDimension();

/*
				double posX,posY;
				if(e.getX()<d.getCenterX()){
					if(e.getY()<d.getCenterY()){
						posX = d.getMaxX();
						posY = d.getMaxY();
					}else{
						posX = d.getMaxX();
						posY = d.getMinY();
					}
				}else{
					if(e.getY()<d.getCenterY()){
						posX = d.getMinX();
						posY = d.getMaxY();
					}else{
						posX = d.getMinX();
						posY = d.getMinY();
					}
				}
				double scaleX = Math.abs(((double)e.getX()-posX)/((double)lastPoint.x-posX));
				double scaleY = Math.abs(((double)e.getY()-posY)/((double)lastPoint.y-posY));
*/

				double posX,posY;
				if(startPoint.x<d.getCenterX()){
					if(startPoint.y<d.getCenterY()){
						posX = d.getMaxX();
						posY = d.getMaxY();
					}else{
						posX = d.getMaxX();
						posY = d.getMinY();
					}
				}else{
					if(startPoint.y<d.getCenterY()){
						posX = d.getMinX();
						posY = d.getMaxY();
					}else{
						posX = d.getMinX();
						posY = d.getMinY();
					}
				}
				double scaleX = Math.abs(((double)e.getX()-posX)/((double)lastPoint.x-posX));
				double scaleY = Math.abs(((double)e.getY()-posY)/((double)lastPoint.y-posY));

				drawNodes.allScaleCenterMove(scaleX,scaleY);

				/*
				double posX,posY,scaleX,scaleY;
				if(lastPoint.x<d.getMinX()){
					if(lastPoint.y<d.getMinY()){
						posX = d.getMaxX();
						posY = d.getMaxY();
						scaleX = ((double)e.getX()-posX)/((double)lastPoint.x-posX);
						scaleY = ((double)e.getY()-posY)/((double)lastPoint.y-posY);
					}else{
						posX = d.getMaxX();
						posY = d.getMinY();
						scaleX = ((double)e.getX()-posX)/((double)lastPoint.x-posX);
						scaleY = ((double)e.getY()-posY)/((double)lastPoint.y-posY);
					}
				}else{
					if(lastPoint.y<d.getMinY()){
						posX = d.getMinX();
						posY = d.getMaxY();
						scaleX = ((double)e.getX()-posX)/((double)lastPoint.x-posX);
						scaleY = ((double)e.getY()-posY)/((double)lastPoint.y-posY);
					}else{
						posX = d.getMinX();
						posY = d.getMinY();
						scaleX = ((double)e.getX()-posX)/((double)lastPoint.x-posX);
						scaleY = ((double)e.getY()-posY)/((double)lastPoint.y-posY);
					}
				}
				scaleX = Math.abs(scaleX);
				scaleY = Math.abs(scaleY);
				*/

			}else{
				drawNodes.allMove(dx, dy);
			}
		}
		lastPoint = e.getPoint();
		update();
	}

	private void accompany_move(StateNode node,double dx,double dy){
		drawNodes.allNodeUnMark();

		LinkedList<StateNode> now_queue = new LinkedList<StateNode>();
		LinkedList<StateNode> next_queue = new LinkedList<StateNode>();

		next_queue.add(node);

		int count=0;
		while(!next_queue.isEmpty()){
			now_queue.clear();
			for(StateNode n : next_queue){
				now_queue.add(n);
			}
			next_queue.clear();

			for(StateNode n : now_queue){
				if(n.isMarked()){ continue; }
				n.mark();
				n.move(dx, dy);

				for(StateNode to : n.getToNodes()){
					next_queue.add(to);
				}
				for(StateNode from : n.getFromNodes()){
					next_queue.add(from);
				}
			}
			dx /= 2;
			dy /= 2;
			count++;
			if(count>10){ break; }
		}
	}

	public void mouseMoved(MouseEvent e) {

	}

	public void mouseWheelMoved(MouseWheelEvent e) {
		double z = Math.sqrt(zoom*10000);
		double dz = (double)e.getWheelRotation()*5;
		if(e.isControlDown()){
			dz /= 5;
		}
		z -= dz;
		if(z<0){ z=0; }
		setZoom(z*z/10000.0);
		update();
	}


	public void keyPressed(KeyEvent e) {
		boolean isUpdate = false;
		double d = 5;
		double s = 1.1;
		if(e.isControlDown()){
			d /= 5;
			s -= 1.0;
			s /= 5;
			s += 1.0;
		}

		switch(e.getKeyCode()){
		case KeyEvent.VK_LEFT:
			if(selectNodes.size()==0){
				if(e.isShiftDown()){
					drawNodes.allScaleCenterMove(1.0/s,1);
				}else{
					drawNodes.allMove(-d/zoom,0);
				}
			}else{
				for(StateNode node : selectNodes){
					node.move(-d/zoom,0);
				}
			}
			isUpdate = true;
			break;
		case KeyEvent.VK_RIGHT:
			if(selectNodes.size()==0){
				if(e.isShiftDown()){
					drawNodes.allScaleCenterMove(s,1);
				}else{
					drawNodes.allMove(d/zoom,0);
				}
			}else{
				for(StateNode node : selectNodes){
					node.move(d/zoom,0);
				}
			}
			isUpdate = true;
			break;
		case KeyEvent.VK_DOWN:
			if(selectNodes.size()==0){
				if(e.isShiftDown()){
					drawNodes.allScaleCenterMove(1,s);
				}else{
					drawNodes.allMove(0,d/zoom);
				}
			}else{
				for(StateNode node : selectNodes){
					node.move(0,d/zoom);
				}
			}
			isUpdate = true;
			break;
		case KeyEvent.VK_UP:
			if(selectNodes.size()==0){
				if(e.isShiftDown()){
					drawNodes.allScaleCenterMove(1,1/s);
				}else{
					drawNodes.allMove(0,-d/zoom);
				}
			}else{
				for(StateNode node : selectNodes){
					node.move(0,-d/zoom);
				}
			}
			isUpdate = true;
			break;
		case KeyEvent.VK_DELETE:
			for(StateNode node : selectNodes){
				drawNodes.remove(node);
			}
			selectNodes.clear();
			update();
			isUpdate = true;
			break;
		}
		if(isUpdate) update();
	}

	public void keyReleased(KeyEvent e) {

	}

	public void keyTyped(KeyEvent e) {

	}


}
