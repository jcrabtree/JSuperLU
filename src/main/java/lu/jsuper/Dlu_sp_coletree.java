/*  Elimination tree computation and layout routines */
package lu.jsuper;

import static lu.jsuper.Dlu_slu_mt_util.SUPERLU_ABORT;
import static lu.jsuper.Dlu_slu_mt_util.SUPERLU_MIN;


public class Dlu_sp_coletree {

	/*
	 *  Implementation of disjoint set union routines.
	 *  Elements are integers in 0..n-1, and the
	 *  names of the sets themselves are of type int.
	 *
	 *  Calls are:
	 *  initialize_disjoint_sets (n) initial call.
	 *  s = make_set (i)             returns a set containing only i.
	 *  s = make_link (t, u)	 returns s = t union u, destroying t and u.
	 *  s = find (i)		 return name of set containing i.
	 *  finalize_disjoint_sets 	 final call.
	 *
	 *  This implementation uses path compression but not weighted union.
	 *  See Tarjan's book for details.
	 *  John Gilbert, CMI, 1987.
	 *
	 *  Implemented path-halving by XSL 07/05/95.
	 */


	@SuppressWarnings("unused")
	static
	int[] mxCallocInt(int n)
	{
	    int i;
	    int buf[];

	    buf = new int [n];
	    if ( buf == null ) {
	         SUPERLU_ABORT("SUPERLU_MALLOC fails for buf in mxCallocInt()");
	       }
	    for (i = 0; i < n; i++) buf[i] = 0;
	    return (buf);
	}

	static
	void initialize_disjoint_sets (
				       int n,
				       int pp[][]
				       )
	{
		(pp[0]) = mxCallocInt(n);
	}


	static
	int make_set (
		      int i,
		      int pp[]
		      )
	{
		pp[i] = i;
		return i;
	}


	static
	int make_link (
		  int s,
		  int t,
		  int pp[]
		  )
	{
		pp[s] = t;
		return t;
	}


	/* PATH HALVING */
	static
	int find (
		  int i,
		  int pp[]
		  )
	{
	    int p, gp;

	    p = pp[i];
	    gp = pp[p];
	    while (gp != p) {
		pp[i] = gp;
		i = gp;
		p = pp[i];
		gp = pp[p];
	    }
	    return (p);
	}

	/* PATH COMPRESSION */
//	static
//	int find (
//		int i
//		)
//	{
//		if (pp[i] != i)
//			pp[i] = find (pp[i]);
//		return pp[i];
//	}

	static
	void finalize_disjoint_sets (
				     int pp[][]
				     )
	{
		pp[0] = null;
	}


	/*
	 *      Find the elimination tree for A'*A.
	 *      This uses something similar to Liu's algorithm.
	 *      It runs in time O(nz(A)*log n) and does not form A'*A.
	 *
	 *      Input:
	 *        Sparse matrix A.  Numeric values are ignored, so any
	 *        explicit zeros are treated as nonzero.
	 *      Output:
	 *        Integer array of parents representing the elimination
	 *        tree of the symbolic product A'*A.  Each vertex is a
	 *        column of A, and nc means a root of the elimination forest.
	 *
	 *      John R. Gilbert, Xerox, 10 Dec 1990
	 *      Based on code by JRG dated 1987, 1988, and 1990.
	 */

	/*
	 * Nonsymmetric elimination tree
	 */
	static
	int
	sp_coletree(
		    int acolst[], int acolend[], /* column start and end past 1 */
		    int arow[],                 /* row indices of A */
		    int nr, int nc,            /* dimension of A */
		    int parent[]	               /* parent in elim tree */
		    )
	{
		int	root[];			/* root of subtee of etree 	*/
		int     firstcol[];		/* first nonzero col in each row*/
		int	rset, cset;
		int	row, col;
		int	rroot;
		int	p;
		int     pp[][] = new int[1][];

		root = mxCallocInt (nc);
		initialize_disjoint_sets (nc, pp);

		/* Compute firstcol[row] = first nonzero column in row */

		firstcol = mxCallocInt (nr);
		for (row = 0; row < nr; firstcol[row++] = nc);
		for (col = 0; col < nc; col++)
			for (p = acolst[col]; p < acolend[col]; p++) {
				row = arow[p];
				firstcol[row] = SUPERLU_MIN(firstcol[row], col);
			}

		/* Compute etree by Liu's algorithm for symmetric matrices,
	           except use (firstcol[r],c) in place of an edge (r,c) of A.
		   Thus each row clique in A'*A is replaced by a star
		   centered at its first vertex, which has the same fill. */

		for (col = 0; col < nc; col++) {
			cset = make_set (col, pp[0]);
			root[cset] = col;
			parent[col] = nc; /* Matlab */
			for (p = acolst[col]; p < acolend[col]; p++) {
				row = firstcol[arow[p]];
				if (row >= col) continue;
				rset = find (row, pp[0]);
				rroot = root[rset];
				if (rroot != col) {
					parent[rroot] = col;
					cset = make_link (cset, rset, pp[0]);
					root[cset] = col;
				}
			}
		}

		finalize_disjoint_sets (pp);
		return 0;
	}

	/*
	 *  q = TreePostorder (n, p);
	 *
	 *	Postorder a tree.
	 *	Input:
	 *	  p is a vector of parent pointers for a forest whose
	 *        vertices are the integers 0 to n-1; p[root]==n.
	 *	Output:
	 *	  q is a vector indexed by 0..n-1 such that q[i] is the
	 *	  i-th vertex in a postorder numbering of the tree.
	 *
	 *        ( 2/7/95 modified by X.Li:
	 *          q is a vector indexed by 0:n-1 such that vertex i is the
	 *          q[i]-th vertex in a postorder numbering of the tree.
	 *          That is, this is the inverse of the previous q. )
	 *
	 *	In the child structure, lower-numbered children are represented
	 *	first, so that a tree which is already numbered in postorder
	 *	will not have its order changed.
	 *
	 *  Written by John Gilbert, Xerox, 10 Dec 1990.
	 *  Based on code written by John Gilbert at CMI in 1987.
	 */

	static
	/*
	 * Depth-first search from vertex v.
	 */
	void etdfs (
		    int	  v,
		    int   first_kid[],
		    int   next_kid[],
		    int   post[],
		    int   postnum[]
		    )
	{
		int	w;

		for (w = first_kid[v]; w != -1; w = next_kid[w]) {
			etdfs (w, first_kid, next_kid, post, postnum);
		}
		/* post[postnum++] = v; in Matlab */
		post[v] = (postnum[0])++;    /* Modified by X. Li on 08/10/07 */
	}


	static
	/*
	 * Depth-first search from vertex n.
	 * No recursion.
	 */
	void nr_etdfs (int n, int parent[],
		       int first_kid[], int next_kid[],
		       int post[], int postnum)
	{
	    int current = n, first, next;

	    while (postnum != n){

	        /* no kid for the current node */
	        first = first_kid[current];

	        /* no first kid for the current node */
	        if (first == -1){

	            /* numbering this node because it has no kid */
	            post[current] = postnum++;

	            /* looking for the next kid */
	            next = next_kid[current];

	            while (next == -1){

	                /* no more kids : back to the parent node */
	                current = parent[current];

	                /* numbering the parent node */
	                post[current] = postnum++;

	                /* get the next kid */
	                next = next_kid[current];
		    }

	            /* stopping criterion */
	            if (postnum==n+1) return;

	            /* updating current node */
	            current = next;
	        }
	        /* updating current node */
	        else {
	            current = first;
		}
	    }
	}

	/*
	 * Post order a tree
	 */
	@SuppressWarnings("unused")
	static
	int[] TreePostorder(
			   int n,
			   int parent[]
			   )
	{
	        int	first_kid[], next_kid[];	/* Linked list of children.	*/
	        int	post[], postnum[] = new int[1];
		int	v, dad;

		/* Allocate storage for working arrays and results	*/
		first_kid = 	mxCallocInt (n+1);
		next_kid  = 	mxCallocInt (n+1);
		post	  = 	mxCallocInt (n+1);

		/* Set up structure describing children */
		for (v = 0; v <= n; first_kid[v++] = -1);
		for (v = n-1; v >= 0; v--) {
			dad = parent[v];
			next_kid[v] = first_kid[dad];
			first_kid[dad] = v;
		}

		/* Depth-first search from dummy root vertex #n */
		postnum[0] = 0;
	if (false) {
		/* recursion */
		etdfs (n, first_kid, next_kid, post, postnum);
	} else {
		/* no recursion */
		nr_etdfs(n, parent, first_kid, next_kid, post, postnum[0]);
	}

		return post;
	}


	/*
	 *      p = spsymetree (A);
	 *
	 *      Find the elimination tree for symmetric matrix A.
	 *      This uses Liu's algorithm, and runs in time O(nz*log n).
	 *
	 *      Input:
	 *        Square sparse matrix A.  No check is made for symmetry;
	 *        elements below and on the diagonal are ignored.
	 *        Numeric values are ignored, so any explicit zeros are
	 *        treated as nonzero.
	 *      Output:
	 *        Integer array of parents representing the etree, with n
	 *        meaning a root of the elimination forest.
	 *      Note:
	 *        This routine uses only the upper triangle, while sparse
	 *        Cholesky (as in spchol.c) uses only the lower.  Matlab's
	 *        dense Cholesky uses only the upper.  This routine could
	 *        be modified to use the lower triangle either by transposing
	 *        the matrix or by traversing it by rows with auxiliary
	 *        pointer and link arrays.
	 *
	 *      John R. Gilbert, Xerox, 10 Dec 1990
	 *      Based on code by JRG dated 1987, 1988, and 1990.
	 *      Modified by X.S. Li, November 1999.
	 */

	/*
	 * Symmetric elimination tree
	 */
	static
	int
	sp_symetree(
		    int acolst[], int acolend[], /* column starts and ends past 1 */
		    int arow[],            /* row indices of A */
		    int n,                /* dimension of A */
		    int parent[]	    /* parent in elim tree */
		    )
	{
		int	root[];		    /* root of subtree of etree 	*/
		int	rset, cset;
		int	row, col;
		int	rroot;
		int	p;
		int     pp[][] = new int[1][];

		root = mxCallocInt (n);
		initialize_disjoint_sets (n, pp);

		for (col = 0; col < n; col++) {
			cset = make_set (col, pp[0]);
			root[cset] = col;
			parent[col] = n; /* Matlab */
			for (p = acolst[col]; p < acolend[col]; p++) {
				row = arow[p];
				if (row >= col) continue;
				rset = find (row, pp[0]);
				rroot = root[rset];
				if (rroot != col) {
					parent[rroot] = col;
					cset = make_link (cset, rset, pp[0]);
					root[cset] = col;
				}
			}
		}
		finalize_disjoint_sets (pp);
		return 0;
	} /* SP_SYMETREE */

}