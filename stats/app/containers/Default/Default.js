import React from 'react'
import { Page } from 'containers/Page/Page';
import { Nav } from 'components/Nav/Nav';
import { Sidebar } from 'components/Sidebar/Sidebar';
import { login, getNodes, getNotaries } from 'scripts/restCalls';
import { mutateNodes,  } from 'scripts/processData';
import { headersList } from 'scripts/headersList'
import navOptions from 'navOptions.json';
import { isNotary, sortNodes } from 'scripts/processData';

export default class Default extends React.Component {
  constructor(props) {
    super(props)
    this.state = {
      nodes: [],
      notaries: [],
      page: 'home'
    }
    
    this.getNodes = this.getNodes.bind(this);
    this.sortTable = this.sortTable.bind(this);
  }

  componentDidMount(){    
    this.getNodes();
  }

  getNodes = async function(){
    let notaries = await getNotaries();
    let nodes = await getNodes();
    nodes = await mutateNodes(nodes);
    nodes = isNotary(nodes, notaries);
    nodes = sortNodes('Organisational Unit', nodes, headersList);
    
    this.setState({
      nodes: nodes,
      notaries: notaries
    });
  }

  sortTable(e){
    let sortedNodes = sortNodes(e.target.dataset.header, this.state.nodes, headersList)
    this.setState({nodes: sortedNodes});
  }

  handleBtn = (event, data) => {
    const btnType = event.target.dataset.btn

    switch (btnType.toLowerCase()) {
      case 'swagger':
        this.setState({page: 'swagger'})
        break
      case 'dashboard':
        this.setState({page: 'home'});
        break;
      default:
        break;
    }
  }

  render () {
    return (
      <div className='default-component'>
        <Nav toggleModal={this.props.toggleModal} />
        <div className="row">
          <Sidebar 
            navOptions={navOptions} 
            handleBtn={this.handleBtn}/> 
          <section id="main-content" className="column column-offset-20">
            <Page           
              headersList={headersList}
              nodes={this.state.nodes}
              notaries={this.state.notaries}
              page={this.state.page} 
              sortTable={this.sortTable}
            /> 
          </section>
        </div>        
      </div>    
    )
  }
}