import React from 'react';
import PropTypes from 'prop-types'

export const Table = (props) => {
  const { headersList, rowData, sortTable, admin, toggleModal } = props;
  return(
    <div className="row grid-responsive mt-2">
      <div className="column ">
        <div className="card" >
          <div className="card-block">
            <div className="table-component">
              <table>
                <TableHead 
                  headersList={headersList}
                  sortTable={sortTable}
                  admin={admin}
                />                  
                <TableBody 
                  headersList={headersList}
                  rowData={rowData}
                  toggleModal={toggleModal}
                  admin={admin}
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
  const { headersList, sortTable, admin } = props;
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
        { admin ? <th>Controls</th> : null }
      </tr>
    </thead>
  )
}

const TableBody = (props) => {
  const { headersList, rowData, toggleModal, admin } = props;
  let tr = rowData.map((node, index) => {
      return ( 
      <TableRow 
        key={index} 
        node={node} 
        headersList={headersList} 
        toggleModal={toggleModal}
        admin={admin} /> );
  })  
  return (
      <tbody>{tr}</tbody>
  );
}

const TableRow = (props) => {
  const { node, headersList, toggleModal, admin } = props;
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
      { (admin) ? 
        <td key={9}>
          <button 
            className='wibble' 
            data-link="delete" 
            onClick={ e => { toggleModal(e, node) }} >
              <em className="fa fa-trash"></em> 
          </button> 
        </td> : null
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