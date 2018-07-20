import React from 'react';
import PropTypes from 'prop-types'

export const Table = (props) => {
  const { headersList, rowData, sortTable } = props;
  return(
    <div className="row grid-responsive mt-2">
      <div className="column ">
        <div className="card" >
          <div className="card-block">
            <div className="table-component">
              <table>
                <TableHead 
                  headersList={headersList}
                  sortTable={sortTable}/>
                <TableBody 
                  headersList={headersList}
                  rowData={rowData}
                />  
              </table>
            </div>
          </div>
        </div>
      </div>
    </div>
  )
}

const TableHead = (props) => {
  const { headersList, sortTable } = props;
  const sortCol = (e) => {
    sortTable(e)
  }
  return(
    <thead>
      <tr>
      {
        Array.from(headersList.values()).map((h, index) => (
          <th key={index} data-header={h} onClick={e => sortCol(e)}>{h}<img src='png/sort.png' data-header={h}/></th>
        ))
      }
      </tr>
    </thead>
  )
}

const TableBody = (props) => {
  const { headersList, rowData } = props;
  let tr = rowData.map((node, index) => {
      return ( <TableRow key={index} node={node} headersList={headersList} /> );
  })  
  return (
      <tbody>{tr}</tbody>
  );
}

const TableRow = (props) => {
  const { node, headersList } = props;
  const valueArray = [];
  Object.keys(node).forEach((key,index) => {
    if(headersList.has(key))
      valueArray.push(node[key])
  });
  return (
    <tr className="table-row-component">
      {
        valueArray.map((value, index) => {
          let td
          typeof value === "boolean" ?  
            td =  <td className={value.toString()} key={index}></td> 
            : 
            td =  <td key={index}>{value.toString()}</td>         
          return td;
        })
      }
    </tr>
  );
}


Table.propTypes = {
  headersList: PropTypes.object.isRequired,
  rowData: PropTypes.array.isRequired
}

TableHead.propTypes = {
  headersList: PropTypes.object.isRequired
}

TableBody.propTypes = {
  rowData: PropTypes.array.isRequired
}

TableRow.propTypes = {
  nodes: PropTypes.object
}